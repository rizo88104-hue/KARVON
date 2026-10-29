package uz.karvon.payment.api;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.assertj.MockMvcTester;
import org.springframework.test.web.servlet.assertj.MvcTestResult;
import uz.karvon.AbstractIntegrationTest;
import uz.karvon.TestLogins;
import uz.karvon.order.application.UnpaidOrderCancelJob;
import uz.karvon.wallet.application.WalletService;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static uz.karvon.TestLogins.newPhone;
import static uz.karvon.TestLogins.read;
import static uz.karvon.payment.PaymentTestSupport.ORDER_TOTAL;
import static uz.karvon.payment.PaymentTestSupport.newOrder;
import static uz.karvon.payment.PaymentTestSupport.orderId;
import static uz.karvon.payment.PaymentTestSupport.orderStatus;
import static uz.karvon.payment.PaymentTestSupport.status;
import static uz.karvon.payment.PaymentTestSupport.toWarehouse;

/**
 * Payme Merchant API as Payme Business calls it (JSON-RPC 2.0, Basic auth "Paycom:test-payme-key").
 */
class PaymeIntegrationTest extends AbstractIntegrationTest {

    private static final String AUTH = "Basic " + Base64.getEncoder()
            .encodeToString("Paycom:test-payme-key".getBytes(StandardCharsets.UTF_8));
    private static final long TIYIN = ORDER_TOTAL * 100;

    @Autowired
    private MockMvcTester mvc;

    @Autowired
    private WalletService wallets;

    @Autowired
    private UnpaidOrderCancelJob cancelJob;

    @Autowired
    private JdbcTemplate jdbc;

    private String customer;
    private String customerId;
    private String admin;
    private String number;

    @BeforeEach
    void order() {
        MvcTestResult login = TestLogins.login(mvc, newPhone());
        customer = read(login, "$.accessToken");
        customerId = read(login, "$.user.id");
        admin = TestLogins.adminToken(mvc);
        number = newOrder(mvc, customer);
    }

    @Test
    void protocolErrors() {
        MvcTestResult wrongKey = mvc.post().uri("/api/v1/payments/payme").contentType(MediaType.APPLICATION_JSON)
                .header(HttpHeaders.AUTHORIZATION, "Basic " + Base64.getEncoder().encodeToString("Paycom:wrong".getBytes()))
                .content(request("CheckTransaction", "{\"id\":\"x\"}")).exchange();
        assertThat(wrongKey).hasStatusOk();
        assertThat(wrongKey).bodyJson().extractingPath("$.error.code").isEqualTo(-32504);
        assertThat(wrongKey).bodyJson().extractingPath("$.id").isEqualTo(7);
        assertThat(wrongKey).bodyJson().extractingPath("$.error.message.uz").isNotNull();

        assertThat(mvc.post().uri("/api/v1/payments/payme").content(request("CheckTransaction", "{\"id\":\"x\"}")))
                .bodyJson().extractingPath("$.error.code").isEqualTo(-32504);
        assertThat(mvc.get().uri("/api/v1/payments/payme")).bodyJson().extractingPath("$.error.code").isEqualTo(-32300);
        assertThat(raw("{not json")).bodyJson().extractingPath("$.error.code").isEqualTo(-32700);
        assertThat(raw("{\"id\":1,\"params\":{}}")).bodyJson().extractingPath("$.error.code").isEqualTo(-32600);

        MvcTestResult unknown = rpc("DoMagic", "{}");
        assertThat(unknown).bodyJson().extractingPath("$.error.code").isEqualTo(-32601);
        assertThat(unknown).bodyJson().extractingPath("$.error.data").isEqualTo("DoMagic");
    }

    @Test
    void checkPerformTransaction() {
        assertThat(rpc("CheckPerformTransaction", check(number, TIYIN)))
                .bodyJson().extractingPath("$.result.allow").isEqualTo(true);
        assertThat(rpc("CheckPerformTransaction", check(number, TIYIN - 100)))
                .bodyJson().extractingPath("$.error.code").isEqualTo(-31001);

        MvcTestResult unknown = rpc("CheckPerformTransaction", check("KRV-999999", TIYIN));
        assertThat(unknown).bodyJson().extractingPath("$.error.code").isEqualTo(-31050);
        assertThat(unknown).bodyJson().extractingPath("$.error.data").isEqualTo("order");
        assertThat(unknown).bodyJson().extractingPath("$.error.message.ru").isEqualTo("Заказ не найден");

        // Nothing is due for the shipping difference yet.
        assertThat(rpc("CheckPerformTransaction", check(number + "-SHIP", 100)))
                .bodyJson().extractingPath("$.error.code").isEqualTo(-31051);
    }

    @Test
    void fullSequenceWithRepeatsAndCancelAfterPerform() {
        String id = paymeId();
        long time = System.currentTimeMillis();

        MvcTestResult created = rpc("CreateTransaction", create(id, time, number, TIYIN));
        assertThat(created).bodyJson().extractingPath("$.result.state").isEqualTo(1);
        String transaction = read(created, "$.result.transaction");
        String createTime = read(created, "$.result.create_time");

        // Repeated CreateTransaction: the same answer.
        MvcTestResult again = rpc("CreateTransaction", create(id, time, number, TIYIN));
        assertThat(read(again, "$.result.transaction")).isEqualTo(transaction);
        assertThat(read(again, "$.result.create_time")).isEqualTo(createTime);
        // Another transaction for the same order while this one waits.
        MvcTestResult other = rpc("CreateTransaction", create(paymeId(), time, number, TIYIN));
        assertThat(other).bodyJson().extractingPath("$.error.code").isEqualTo(-31052);

        MvcTestResult waiting = rpc("CheckTransaction", "{\"id\":\"%s\"}".formatted(id));
        assertThat(waiting).bodyJson().extractingPath("$.result.state").isEqualTo(1);
        assertThat(waiting).bodyJson().extractingPath("$.result.perform_time").isEqualTo(0);
        assertThat(waiting).bodyJson().extractingPath("$.result.reason").isNull();

        MvcTestResult performed = rpc("PerformTransaction", "{\"id\":\"%s\"}".formatted(id));
        assertThat(performed).bodyJson().extractingPath("$.result.state").isEqualTo(2);
        assertThat(performed).bodyJson().extractingPath("$.result.transaction").isEqualTo(transaction);
        assertThat(orderStatus(mvc, customer, number)).isEqualTo("PAID");

        // Repeated PerformTransaction: the same perform_time, paid only once.
        String performTime = read(performed, "$.result.perform_time");
        assertThat(read(rpc("PerformTransaction", "{\"id\":\"%s\"}".formatted(id)), "$.result.perform_time"))
                .isEqualTo(performTime);
        assertThat(rpc("CheckPerformTransaction", check(number, TIYIN)))
                .bodyJson().extractingPath("$.error.code").isEqualTo(-31051);

        // Cancel after perform: the money goes back to the card, the order is cancelled, the wallet stays empty.
        MvcTestResult cancelled = rpc("CancelTransaction", "{\"id\":\"%s\",\"reason\":5}".formatted(id));
        assertThat(cancelled).bodyJson().extractingPath("$.result.state").isEqualTo(-2);
        String cancelTime = read(cancelled, "$.result.cancel_time");
        assertThat(orderStatus(mvc, customer, number)).isEqualTo("CANCELLED");
        assertThat(wallets.balance(UUID.fromString(customerId))).isEqualByComparingTo("0");

        assertThat(read(rpc("CancelTransaction", "{\"id\":\"%s\",\"reason\":5}".formatted(id)), "$.result.cancel_time"))
                .isEqualTo(cancelTime);
        assertThat(rpc("PerformTransaction", "{\"id\":\"%s\"}".formatted(id)))
                .bodyJson().extractingPath("$.error.code").isEqualTo(-31008);

        MvcTestResult checked = rpc("CheckTransaction", "{\"id\":\"%s\"}".formatted(id));
        assertThat(checked).bodyJson().extractingPath("$.result.state").isEqualTo(-2);
        assertThat(checked).bodyJson().extractingPath("$.result.reason").isEqualTo(5);

        MvcTestResult statement = rpc("GetStatement", "{\"from\":%d,\"to\":%d}".formatted(time - 1, time + 1));
        assertThat(statement).bodyJson().extractingPath("$.result.transactions[?(@.id == '%s')].state".formatted(id))
                .asArray().containsExactly(-2);
        assertThat(statement).bodyJson().extractingPath("$.result.transactions[?(@.id == '%s')].account.order".formatted(id))
                .asArray().containsExactly(number);
        assertThat(statement).bodyJson().extractingPath("$.result.transactions[?(@.id == '%s')].amount".formatted(id))
                .asArray().containsExactly((int) TIYIN);
    }

    @Test
    void cancelOfAWaitingTransactionKeepsTheOrderUnpaid() {
        String id = paymeId();
        rpc("CreateTransaction", create(id, System.currentTimeMillis(), number, TIYIN));

        MvcTestResult cancelled = rpc("CancelTransaction", "{\"id\":\"%s\",\"reason\":3}".formatted(id));

        assertThat(cancelled).bodyJson().extractingPath("$.result.state").isEqualTo(-1);
        assertThat(orderStatus(mvc, customer, number)).isEqualTo("AWAITING_PAYMENT");
        assertThat(rpc("CreateTransaction", create(id, System.currentTimeMillis(), number, TIYIN)))
                .bodyJson().extractingPath("$.error.code").isEqualTo(-31008);
        // A new transaction is fine once the old one is cancelled.
        assertThat(rpc("CreateTransaction", create(paymeId(), System.currentTimeMillis(), number, TIYIN)))
                .bodyJson().extractingPath("$.result.state").isEqualTo(1);
    }

    @Test
    void cannotCancelOnceTheGoodsAreBeingBought() {
        String id = paymeId();
        rpc("CreateTransaction", create(id, System.currentTimeMillis(), number, TIYIN));
        rpc("PerformTransaction", "{\"id\":\"%s\"}".formatted(id));
        status(mvc, admin, orderId(mvc, admin, number), "{\"toStatus\":\"PURCHASING\"}");

        assertThat(rpc("CancelTransaction", "{\"id\":\"%s\",\"reason\":5}".formatted(id)))
                .bodyJson().extractingPath("$.error.code").isEqualTo(-31007);
        assertThat(rpc("CheckTransaction", "{\"id\":\"%s\"}".formatted(id)))
                .bodyJson().extractingPath("$.result.state").isEqualTo(2);
    }

    @Test
    void transactionOlderThanTwelveHoursIsCancelledOnPerform() {
        String id = paymeId();
        long thirteenHoursAgo = System.currentTimeMillis() - 13 * 3_600_000L;
        rpc("CreateTransaction", create(id, thirteenHoursAgo, number, TIYIN));

        assertThat(rpc("PerformTransaction", "{\"id\":\"%s\"}".formatted(id)))
                .bodyJson().extractingPath("$.error.code").isEqualTo(-31008);
        MvcTestResult checked = rpc("CheckTransaction", "{\"id\":\"%s\"}".formatted(id));
        assertThat(checked).bodyJson().extractingPath("$.result.state").isEqualTo(-1);
        assertThat(checked).bodyJson().extractingPath("$.result.reason").isEqualTo(4);
        assertThat(orderStatus(mvc, customer, number)).isEqualTo("AWAITING_PAYMENT");
    }

    @Test
    void unpaidOrderWithAWaitingPaymeTransactionIsNotAutoCancelled() {
        rpc("CreateTransaction", create(paymeId(), System.currentTimeMillis(), number, TIYIN));
        jdbc.update("UPDATE orders SET created_at = now() - interval '31 minutes' WHERE number = ?", number);

        cancelJob.cancelUnpaid();

        assertThat(orderStatus(mvc, customer, number)).isEqualTo("AWAITING_PAYMENT");
    }

    @Test
    void shippingDifferenceIsPaidThroughItsOwnAccount() {
        String orderId = orderId(mvc, admin, number);
        toWarehouse(mvc, admin, orderId, "1.2", true);
        assertThat(orderStatus(mvc, customer, number)).isEqualTo("AWAITING_SHIPPING_PAYMENT");
        String id = paymeId();

        assertThat(rpc("CheckPerformTransaction", check(number + "-SHIP", 3_200_000)))
                .bodyJson().extractingPath("$.result.allow").isEqualTo(true);
        rpc("CreateTransaction", create(id, System.currentTimeMillis(), number + "-SHIP", 3_200_000));
        assertThat(rpc("PerformTransaction", "{\"id\":\"%s\"}".formatted(id)))
                .bodyJson().extractingPath("$.result.state").isEqualTo(2);

        assertThat(status(mvc, admin, orderId, "{\"toStatus\":\"IN_TRANSIT\"}"))
                .bodyJson().extractingPath("$.order.status").isEqualTo("IN_TRANSIT");
    }

    private MvcTestResult rpc(String method, String params) {
        return mvc.post().uri("/api/v1/payments/payme").contentType(MediaType.parseMediaType("text/json;charset=UTF-8"))
                .header(HttpHeaders.AUTHORIZATION, AUTH).content(request(method, params)).exchange();
    }

    private MvcTestResult raw(String body) {
        return mvc.post().uri("/api/v1/payments/payme").contentType(MediaType.APPLICATION_JSON)
                .header(HttpHeaders.AUTHORIZATION, AUTH).content(body).exchange();
    }

    private static String request(String method, String params) {
        return "{\"method\":\"%s\",\"params\":%s,\"id\":7}".formatted(method, params);
    }

    private static String check(String account, long amount) {
        return "{\"amount\":%d,\"account\":{\"order\":\"%s\"}}".formatted(amount, account);
    }

    private static String create(String id, long time, String account, long amount) {
        return "{\"id\":\"%s\",\"time\":%d,\"amount\":%d,\"account\":{\"order\":\"%s\"}}".formatted(id, time, amount, account);
    }

    /** A Payme Business id: 24 characters. */
    private static String paymeId() {
        return UUID.randomUUID().toString().replace("-", "").substring(0, 24);
    }
}
