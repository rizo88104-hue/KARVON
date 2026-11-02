package uz.karvon.e2e;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.assertj.MockMvcTester;
import org.springframework.test.web.servlet.assertj.MvcTestResult;
import uz.karvon.AbstractIntegrationTest;
import uz.karvon.TestLogins;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;

import static org.assertj.core.api.Assertions.assertThat;
import static uz.karvon.TestLogins.bearer;
import static uz.karvon.TestLogins.newPhone;
import static uz.karvon.TestLogins.read;

/**
 * Whole order lifecycles through the public API only, with the dev/test fakes (Taobao catalog, SMS, Payme test key):
 * customer, BUYER and OPERATOR each use their own endpoints. Checks the statuses, the wallet ledger, the
 * notification outbox and the audit log at the end of each story.
 */
class OrderLifecycleE2ETest extends AbstractIntegrationTest {

    /** Fake catalog: "Ayollar oq krossovkasi", 89 CNY, 0.8 kg; one item costs 238 200 so'm in total. */
    private static final String PRODUCT = "680000002234";
    private static final long ORDER_TOTAL = 238_200;
    private static final String PAYME_AUTH = "Basic " + Base64.getEncoder()
            .encodeToString("Paycom:test-payme-key".getBytes(StandardCharsets.UTF_8));
    private static final AtomicLong RPC_ID = new AtomicLong();

    @Autowired
    private MockMvcTester mvc;

    @Autowired
    private JdbcTemplate jdbc;

    private String customer;
    private String buyer;
    private String operator;

    @BeforeEach
    void actors() {
        customer = otpLogin(newPhone());
        buyer = TestLogins.staffToken(mvc, "BUYER");
        operator = TestLogins.staffToken(mvc, "OPERATOR");
    }

    @Test
    void happyPathWithAHeavierParcel() {
        String number = orderOneItemFoundBySearch();
        payWithPayme(number, ORDER_TOTAL);
        assertThat(customerStatus(number)).isEqualTo("PAID");
        String orderId = orderIdAs(operator, number);

        // Buyer in China: take the order from the queue and buy the item on Taobao.
        assertThat(read(as(buyer, mvc.get().uri("/api/v1/admin/procurement/queue")), "$.content[*].number")).contains(number);
        assertThat(field(as(buyer, mvc.post().uri("/api/v1/admin/procurement/orders/{id}/start", orderId)), "$.order.status"))
                .isEqualTo("PURCHASING");
        String itemId = read(as(buyer, mvc.get().uri("/api/v1/admin/orders/{id}", orderId)), "$.order.items[0].id");
        MvcTestResult purchased = asJson(buyer, mvc.post().uri("/api/v1/admin/procurement/items/{id}/purchased", itemId),
                "{\"actualPriceCny\":89,\"taobaoOrderNumber\":\"TB-E2E-1\",\"chinaTrackingNumber\":\"SF-E2E-1\"}");
        assertThat(field(purchased, "$.order.status")).isEqualTo("PURCHASED");

        // Warehouse: 1.2 kg instead of the expected 0.8 kg -> 0.4 kg x 80 000 = 32 000 so'm to pay.
        MvcTestResult weighed = asJson(buyer, mvc.post().uri("/api/v1/admin/orders/{id}/weight", orderId),
                "{\"realWeightKg\":1.2}");
        assertThat(field(weighed, "$.order.status")).isEqualTo("AWAITING_SHIPPING_PAYMENT");
        assertThat(field(weighed, "$.order.amountDueUzs")).isEqualTo("32000");

        // The operator cannot ship before the difference is paid.
        MvcTestResult tooEarly = ship(orderId);
        assertThat(tooEarly).hasStatus(HttpStatus.CONFLICT);
        assertThat(field(tooEarly, "$.code")).isEqualTo("SHIPPING_DIFFERENCE_UNPAID");

        payWithPayme(number + "-SHIP", 32_000);
        assertThat(field(as(customer, mvc.get().uri("/api/v1/orders/{n}", number)), "$.amountDueUzs")).isEqualTo("0");

        // Operator in Tashkent: ship, arrived, delivered.
        assertThat(field(ship(orderId), "$.order.status")).isEqualTo("IN_TRANSIT");
        assertThat(field(asJson(operator, mvc.post().uri("/api/v1/admin/orders/{id}/arrived", orderId),
                "{\"pickupInfo\":\"Chilonzor 9, kargo ofisi\"}"), "$.order.status")).isEqualTo("ARRIVED");
        assertThat(field(asJson(operator, mvc.post().uri("/api/v1/admin/orders/{id}/delivered", orderId), "{}"),
                "$.order.status")).isEqualTo("DELIVERED");

        MvcTestResult order = as(customer, mvc.get().uri("/api/v1/orders/{n}", number));
        assertThat(field(order, "$.status")).isEqualTo("DELIVERED");
        assertThat(field(order, "$.paidUzs")).isEqualTo(String.valueOf(ORDER_TOTAL + 32_000));
        assertThat(field(order, "$.realWeightKg")).isEqualTo("1.2");
        assertThat(field(order, "$.internationalTrackingNumber")).isEqualTo("CN-E2E-777");

        // Paid by card only: the wallet ledger stays empty.
        MvcTestResult wallet = as(customer, mvc.get().uri("/api/v1/me/wallet"));
        assertThat(field(wallet, "$.balanceUzs")).isEqualTo("0");
        assertThat(field(wallet, "$.transactions.totalElements")).isEqualTo("0");

        assertThat(outbox(orderId)).containsExactly(
                "order.paid", "order.purchased", "order.at_warehouse", "order.shipping_difference",
                "payment.received", "order.in_transit", "order.arrived");
        assertThat(audit(orderId)).containsExactly(
                "BUYER PROCUREMENT_START", "BUYER ORDER_WEIGH", "OPERATOR ORDER_SHIP", "OPERATOR ORDER_ARRIVED",
                "OPERATOR ORDER_DELIVERED");
        assertThat(audit(itemId)).containsExactly("BUYER PROCUREMENT_ITEM_PURCHASED");
    }

    @Test
    void outOfStockIsRefundedToTheWallet() {
        String number = orderOneItemFoundBySearch();
        payWithPayme(number, ORDER_TOTAL);
        String orderId = orderIdAs(operator, number);
        as(buyer, mvc.post().uri("/api/v1/admin/procurement/orders/{id}/start", orderId));
        String itemId = read(as(buyer, mvc.get().uri("/api/v1/admin/orders/{id}", orderId)), "$.order.items[0].id");

        MvcTestResult missing = asJson(buyer, mvc.post().uri("/api/v1/admin/procurement/items/{id}/out-of-stock", itemId),
                "{\"comment\":\"Sotuvchida qolmagan\"}");
        assertThat(field(missing, "$.order.status")).isEqualTo("OUT_OF_STOCK");
        assertThat(customerStatus(number)).isEqualTo("OUT_OF_STOCK");

        // The only item is gone, so the customer can only take the money back.
        MvcTestResult refunded = asJson(customer, mvc.post().uri("/api/v1/orders/{n}/decision", number), "{\"action\":\"REFUND\"}");
        assertThat(field(refunded, "$.status")).isEqualTo("CANCELLED");
        assertThat(field(refunded, "$.refundedUzs")).isEqualTo(String.valueOf(ORDER_TOTAL));

        MvcTestResult wallet = as(customer, mvc.get().uri("/api/v1/me/wallet"));
        assertThat(field(wallet, "$.balanceUzs")).isEqualTo(String.valueOf(ORDER_TOTAL));
        assertThat(field(wallet, "$.transactions.totalElements")).isEqualTo("1");
        assertThat(field(wallet, "$.transactions.content[0].type")).isEqualTo("REFUND");
        assertThat(field(wallet, "$.transactions.content[0].amountUzs")).isEqualTo(String.valueOf(ORDER_TOTAL));
        assertThat(field(wallet, "$.transactions.content[0].orderId")).isEqualTo(orderId);

        assertThat(outbox(orderId)).containsExactly("order.paid", "order.out_of_stock");
        assertThat(audit(orderId)).containsExactly("BUYER PROCUREMENT_START");
        assertThat(audit(itemId)).containsExactly("BUYER PROCUREMENT_ITEM_OUT_OF_STOCK");
    }

    @Test
    void priceChangeAcceptedAndTheDifferencePaid() {
        String number = orderOneItemFoundBySearch();
        payWithPayme(number, ORDER_TOTAL);
        String orderId = orderIdAs(operator, number);
        as(buyer, mvc.post().uri("/api/v1/admin/procurement/orders/{id}/start", orderId));
        String itemId = read(as(buyer, mvc.get().uri("/api/v1/admin/orders/{id}", orderId)), "$.order.items[0].id");

        // 100 CNY instead of 89: more than the 3 % threshold, so the customer decides.
        MvcTestResult dearer = asJson(buyer, mvc.post().uri("/api/v1/admin/procurement/items/{id}/purchased", itemId),
                "{\"actualPriceCny\":100,\"taobaoOrderNumber\":\"TB-E2E-3\"}");
        assertThat(field(dearer, "$.order.status")).isEqualTo("PRICE_CHANGED");

        MvcTestResult accepted = asJson(customer, mvc.post().uri("/api/v1/orders/{n}/decision", number), "{\"action\":\"ACCEPT\"}");
        assertThat(field(accepted, "$.status")).isEqualTo("PURCHASED");
        long difference = Long.parseLong(field(accepted, "$.priceDifferenceUzs"));
        assertThat(difference).as("11 CNY more, with service fee").isPositive();
        assertThat(field(accepted, "$.amountDueUzs")).isEqualTo(String.valueOf(difference));

        payWithPayme(number + "-PRICE", difference);
        MvcTestResult order = as(customer, mvc.get().uri("/api/v1/orders/{n}", number));
        assertThat(field(order, "$.amountDueUzs")).isEqualTo("0");
        assertThat(field(order, "$.paidUzs")).isEqualTo(String.valueOf(ORDER_TOTAL + difference));
        assertThat(new java.math.BigDecimal(field(order, "$.items[0].actualPriceCny"))).isEqualByComparingTo("100");

        // Ready for the warehouse step like any other purchased order.
        assertThat(field(asJson(buyer, mvc.post().uri("/api/v1/admin/orders/{id}/weight", orderId), "{\"realWeightKg\":0.8}"),
                "$.order.status")).isEqualTo("AT_WAREHOUSE");

        MvcTestResult wallet = as(customer, mvc.get().uri("/api/v1/me/wallet"));
        assertThat(field(wallet, "$.transactions.totalElements")).isEqualTo("0");
        assertThat(outbox(orderId)).containsExactly(
                "order.paid", "order.price_changed", "order.price_difference", "order.purchased",
                "payment.received", "order.at_warehouse");
        assertThat(audit(orderId)).containsExactly("BUYER PROCUREMENT_START", "BUYER ORDER_WEIGH");
        assertThat(audit(itemId)).containsExactly("BUYER PROCUREMENT_ITEM_PURCHASED");
    }

    // ---- the customer's steps

    /** OTP login exactly as the site does it: request a code, read it (dev/test endpoint), verify. */
    private String otpLogin(String phone) {
        assertThat(TestLogins.requestOtp(mvc, phone)).hasStatusOk();
        MvcTestResult verified = TestLogins.verifyOtp(mvc, phone, TestLogins.lastCode(mvc, phone));
        assertThat(verified).hasStatusOk();
        return read(verified, "$.accessToken");
    }

    /** Search in Uzbek, open the product, put one in-stock SKU into the cart and order it (payment by Payme). */
    private String orderOneItemFoundBySearch() {
        MvcTestResult search = mvc.get().uri("/api/v1/catalog/search?q=krossovka").exchange();
        assertThat(search).hasStatusOk();
        assertThat(read(search, "$.content[*].externalId")).contains(PRODUCT);
        MvcTestResult product = mvc.get().uri("/api/v1/catalog/products/{id}", PRODUCT).exchange();
        String skuId = read(product, "$.skus[?(@.stock > 0)].skuId").replaceAll("[\\[\\]\"]", "").split(",")[0];

        MvcTestResult cart = asJson(customer, mvc.post().uri("/api/v1/cart/items"),
                "{\"externalProductId\":\"%s\",\"skuId\":\"%s\",\"quantity\":1}".formatted(PRODUCT, skuId));
        assertThat(cart).hasStatus(HttpStatus.CREATED);
        assertThat(field(cart, "$.totals.totalUzs")).isEqualTo(String.valueOf(ORDER_TOTAL));

        MvcTestResult order = asJson(customer, mvc.post().uri("/api/v1/orders"),
                "{\"deliveryMethod\":\"CARGO_PICKUP\",\"paymentMethod\":\"PAYME\"}");
        assertThat(order).hasStatus(HttpStatus.CREATED);
        assertThat(field(order, "$.status")).isEqualTo("AWAITING_PAYMENT");
        return read(order, "$.number");
    }

    /** Payme Business calls the Merchant API: check, create and perform a transaction for the account. */
    private void payWithPayme(String account, long amountUzs) {
        long tiyin = amountUzs * 100;
        String paymeId = UUID.randomUUID().toString().replace("-", "").substring(0, 24);
        long time = System.currentTimeMillis();
        assertThat(field(payme("CheckPerformTransaction",
                "{\"amount\":%d,\"account\":{\"order\":\"%s\"}}".formatted(tiyin, account)), "$.result.allow"))
                .isEqualTo("true");
        assertThat(field(payme("CreateTransaction",
                "{\"id\":\"%s\",\"time\":%d,\"amount\":%d,\"account\":{\"order\":\"%s\"}}".formatted(paymeId, time, tiyin, account)),
                "$.result.state")).isEqualTo("1");
        assertThat(field(payme("PerformTransaction", "{\"id\":\"%s\"}".formatted(paymeId)), "$.result.state"))
                .isEqualTo("2");
    }

    private MvcTestResult payme(String method, String params) {
        MvcTestResult result = mvc.post().uri("/api/v1/payments/payme")
                .contentType(MediaType.APPLICATION_JSON).header(HttpHeaders.AUTHORIZATION, PAYME_AUTH)
                .content("{\"jsonrpc\":\"2.0\",\"id\":%d,\"method\":\"%s\",\"params\":%s}"
                        .formatted(RPC_ID.incrementAndGet(), method, params))
                .exchange();
        assertThat(result).hasStatusOk();
        assertThat(result).bodyJson().doesNotHavePath("$.error");
        return result;
    }

    private String customerStatus(String number) {
        return field(as(customer, mvc.get().uri("/api/v1/orders/{n}", number)), "$.status");
    }

    // ---- staff helpers

    private String orderIdAs(String token, String number) {
        return read(as(token, mvc.get().uri("/api/v1/admin/orders?number={n}", number)), "$.content[0].id");
    }

    private MvcTestResult ship(String orderId) {
        return asJson(operator, mvc.post().uri("/api/v1/admin/orders/{id}/ship", orderId),
                "{\"internationalTrackingNumber\":\"CN-E2E-777\",\"carrier\":\"E2E Cargo\"}");
    }

    // ---- checks on the side effects

    /** Template keys of the notification outbox rows of the order, oldest first (SMS: no Telegram linked). */
    private List<String> outbox(String orderId) {
        return jdbc.queryForList("""
                SELECT template_key FROM notifications WHERE order_id = ?::uuid AND channel = 'SMS'
                ORDER BY created_at, template_key""", String.class, orderId);
    }

    /** "ROLE ACTION" of the audit log rows for an entity, oldest first. */
    private List<String> audit(String entityId) {
        return jdbc.queryForList("SELECT actor_role || ' ' || action FROM audit_events WHERE entity_id = ? ORDER BY created_at",
                String.class, entityId);
    }

    // ---- HTTP

    private MvcTestResult as(String token, MockMvcTester.MockMvcRequestBuilder request) {
        MvcTestResult result = request.header(HttpHeaders.AUTHORIZATION, bearer(token)).exchange();
        assertThat(result.getResponse().getStatus()).as(new String(result.getResponse().getContentAsByteArray(), StandardCharsets.UTF_8))
                .isLessThan(500);
        return result;
    }

    private MvcTestResult asJson(String token, MockMvcTester.MockMvcRequestBuilder request, String body) {
        return as(token, request.contentType(MediaType.APPLICATION_JSON).content(body));
    }

    private static String field(MvcTestResult result, String path) {
        return read(result, path);
    }
}
