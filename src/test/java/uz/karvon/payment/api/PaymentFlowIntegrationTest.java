package uz.karvon.payment.api;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.assertj.MockMvcTester;
import org.springframework.test.web.servlet.assertj.MvcTestResult;
import uz.karvon.AbstractIntegrationTest;
import uz.karvon.TestLogins;
import uz.karvon.wallet.application.WalletService;
import uz.karvon.wallet.domain.WalletTransactionType;

import java.math.BigDecimal;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static uz.karvon.TestLogins.newPhone;
import static uz.karvon.TestLogins.read;
import static uz.karvon.payment.PaymentTestSupport.ORDER_TOTAL;
import static uz.karvon.payment.PaymentTestSupport.as;
import static uz.karvon.payment.PaymentTestSupport.newOrder;
import static uz.karvon.payment.PaymentTestSupport.orderId;
import static uz.karvon.payment.PaymentTestSupport.orderStatus;
import static uz.karvon.payment.PaymentTestSupport.status;
import static uz.karvon.payment.PaymentTestSupport.toWarehouse;

/**
 * Customer payments with the dev/test fake provider and the wallet; refunds and the shipping difference.
 */
class PaymentFlowIntegrationTest extends AbstractIntegrationTest {

    @Autowired
    private MockMvcTester mvc;

    @Autowired
    private WalletService wallets;

    private String customer;
    private UUID customerId;
    private String admin;

    @BeforeEach
    void login() {
        MvcTestResult login = TestLogins.login(mvc, newPhone());
        customer = read(login, "$.accessToken");
        customerId = UUID.fromString(read(login, "$.user.id"));
        admin = TestLogins.adminToken(mvc);
    }

    @Test
    void fakeCheckoutPaysTheOrderAndAdminCancelRefundsToTheWallet() {
        String number = newOrder(mvc, customer);

        MvcTestResult started = pay(number, "/pay", "PAYME");
        assertThat(started).hasStatusOk();
        assertThat(started).bodyJson().extractingPath("$.paid").isEqualTo(false);
        assertThat(started).bodyJson().extractingPath("$.amountUzs").isEqualTo((int) ORDER_TOTAL);
        String checkoutUrl = read(started, "$.checkoutUrl");
        assertThat(checkoutUrl).startsWith("http://localhost:8080/api/v1/dev/pay/");

        String path = checkoutUrl.substring("http://localhost:8080".length());
        MvcTestResult paid = mvc.get().uri(path).exchange();
        assertThat(paid).bodyJson().extractingPath("$.state").isEqualTo("PERFORMED");
        assertThat(orderStatus(mvc, customer, number)).isEqualTo("PAID");
        assertThat(mvc.get().uri(path)).bodyJson().extractingPath("$.state").isEqualTo("PERFORMED");
        assertThat(pay(number, "/pay", "CLICK")).bodyJson().extractingPath("$.code").isEqualTo("ORDER_ALREADY_PAID");

        String orderId = orderId(mvc, admin, number);
        assertThat(status(mvc, admin, orderId, "{\"toStatus\":\"CANCELLED\",\"comment\":\"Mijoz so'radi\"}")).hasStatusOk();

        MvcTestResult wallet = as(mvc, customer, mvc.get().uri("/api/v1/me/wallet"));
        assertThat(wallet).bodyJson().extractingPath("$.balanceUzs").isEqualTo((int) ORDER_TOTAL);
        assertThat(wallet).bodyJson().extractingPath("$.transactions.content[0].type").isEqualTo("REFUND");
        assertThat(wallet).bodyJson().extractingPath("$.transactions.content[0].orderId").isEqualTo(orderId);
    }

    @Test
    void walletPaysAtOnceWhenTheBalanceIsEnough() {
        String number = newOrder(mvc, customer);

        MvcTestResult notEnough = pay(number, "/pay", "WALLET");
        assertThat(notEnough).hasStatus(HttpStatus.BAD_REQUEST);
        assertThat(notEnough).bodyJson().extractingPath("$.code").isEqualTo("INSUFFICIENT_FUNDS");
        assertThat(orderStatus(mvc, customer, number)).isEqualTo("AWAITING_PAYMENT");

        wallets.credit(customerId, new BigDecimal("300000"), WalletTransactionType.REFUND, null, null, "test money");
        MvcTestResult paid = pay(number, "/pay", "WALLET");

        assertThat(paid).bodyJson().extractingPath("$.paid").isEqualTo(true);
        assertThat(paid).bodyJson().extractingPath("$.checkoutUrl").isNull();
        assertThat(orderStatus(mvc, customer, number)).isEqualTo("PAID");
        assertThat(wallets.balance(customerId)).isEqualByComparingTo("61800");
        assertThat(as(mvc, customer, mvc.get().uri("/api/v1/me/wallet"))).bodyJson()
                .extractingPath("$.transactions.content[0].type").isEqualTo("ORDER_PAYMENT");
    }

    @Test
    void heavierParcelIsPaidWithPayShipping() {
        String number = newOrder(mvc, customer);
        String orderId = orderId(mvc, admin, number);
        assertThat(pay(number, "/pay-shipping", "PAYME")).bodyJson().extractingPath("$.code").isEqualTo("NOTHING_TO_PAY");

        toWarehouse(mvc, admin, orderId, "1.2", true);
        assertThat(orderStatus(mvc, customer, number)).isEqualTo("AWAITING_SHIPPING_PAYMENT");

        String checkoutUrl = read(pay(number, "/pay-shipping", "CLICK"), "$.checkoutUrl");
        assertThat(as(mvc, customer, mvc.get().uri("/api/v1/orders/{n}", number)))
                .bodyJson().extractingPath("$.amountDueUzs").isEqualTo(32000);
        mvc.get().uri(checkoutUrl.substring("http://localhost:8080".length())).exchange();

        assertThat(as(mvc, customer, mvc.get().uri("/api/v1/orders/{n}", number)))
                .bodyJson().extractingPath("$.amountDueUzs").isEqualTo(0);
        assertThat(pay(number, "/pay-shipping", "WALLET")).bodyJson().extractingPath("$.code").isEqualTo("ORDER_ALREADY_PAID");
        assertThat(status(mvc, admin, orderId, "{\"toStatus\":\"IN_TRANSIT\"}"))
                .bodyJson().extractingPath("$.order.status").isEqualTo("IN_TRANSIT");
    }

    @Test
    void lighterParcelRefundsTheDifferenceToTheWallet() {
        String number = newOrder(mvc, customer);
        String orderId = orderId(mvc, admin, number);

        toWarehouse(mvc, admin, orderId, "0.6", true);

        assertThat(orderStatus(mvc, customer, number)).isEqualTo("AT_WAREHOUSE");
        MvcTestResult wallet = as(mvc, customer, mvc.get().uri("/api/v1/me/wallet"));
        assertThat(wallet).bodyJson().extractingPath("$.balanceUzs").isEqualTo(16000);
        assertThat(wallet).bodyJson().extractingPath("$.transactions.content[0].type").isEqualTo("SHIPPING_REFUND");
    }

    @Test
    void sameWeightChangesNothing() {
        String number = newOrder(mvc, customer);
        String orderId = orderId(mvc, admin, number);

        toWarehouse(mvc, admin, orderId, "0.8", true);

        assertThat(orderStatus(mvc, customer, number)).isEqualTo("AT_WAREHOUSE");
        assertThat(wallets.balance(customerId)).isEqualByComparingTo("0");
    }

    @Test
    void onlyOwnOrdersCanBePaid() {
        String number = newOrder(mvc, customer);
        String stranger = TestLogins.accessToken(mvc, newPhone());

        assertThat(as(mvc, stranger, mvc.post().uri("/api/v1/orders/{n}/pay", number)
                .contentType(MediaType.APPLICATION_JSON).content("{\"provider\":\"PAYME\"}")))
                .hasStatus(HttpStatus.NOT_FOUND);
        assertThat(mvc.get().uri("/api/v1/dev/pay/{id}", UUID.randomUUID())).hasStatus(HttpStatus.NOT_FOUND);
    }

    private MvcTestResult pay(String number, String path, String provider) {
        return as(mvc, customer, mvc.post().uri("/api/v1/orders/{n}" + path, number)
                .contentType(MediaType.APPLICATION_JSON).content("{\"provider\":\"%s\"}".formatted(provider)));
    }
}
