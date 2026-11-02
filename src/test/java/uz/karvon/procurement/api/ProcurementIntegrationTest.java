package uz.karvon.procurement.api;

import com.jayway.jsonpath.JsonPath;
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

import java.io.UnsupportedEncodingException;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static uz.karvon.TestLogins.newPhone;
import static uz.karvon.TestLogins.read;
import static uz.karvon.payment.PaymentTestSupport.as;
import static uz.karvon.payment.PaymentTestSupport.newOrder;
import static uz.karvon.payment.PaymentTestSupport.orderId;
import static uz.karvon.payment.PaymentTestSupport.orderStatus;
import static uz.karvon.payment.PaymentTestSupport.status;

/**
 * The buyer queue. Orders are 女士小白鞋 SKU 001 (89 CNY, 0.8 kg) = 238 200 UZS; the threshold is 3%.
 */
class ProcurementIntegrationTest extends AbstractIntegrationTest {

    private static final String BASE = "/api/v1/admin/procurement";

    @Autowired
    private MockMvcTester mvc;

    @Autowired
    private WalletService wallets;

    private String customer;
    private UUID customerId;
    private String admin;
    private String buyer;

    @BeforeEach
    void users() {
        MvcTestResult login = TestLogins.login(mvc, newPhone());
        customer = read(login, "$.accessToken");
        customerId = UUID.fromString(read(login, "$.user.id"));
        admin = TestLogins.adminToken(mvc);
        buyer = TestLogins.staffToken(mvc, "BUYER");
    }

    @Test
    void queueShowsPaidOrdersOldestFirstWithWhatTheBuyerNeeds() {
        String unpaid = newOrder(mvc, customer);
        String paid = paidOrder();

        // Other tests leave paid orders too, so look through every page (oldest first).
        MvcTestResult queue = null;
        List<String> numbers = new ArrayList<>();
        for (int page = 0; page < 50; page++) {
            MvcTestResult current = as(mvc, buyer, mvc.get().uri(BASE + "/queue?size=100&page={p}", page));
            assertThat(current).hasStatusOk();
            List<String> onPage = JsonPath.read(text(current), "$.content[*].number");
            numbers.addAll(onPage);
            if (onPage.contains(paid)) {
                queue = current;
            }
            if (onPage.size() < 100) {
                break;
            }
        }
        assertThat(numbers).contains(paid).doesNotContain(unpaid);
        String order = "$.content[?(@.number == '" + paid + "')]";
        assertThat(queue).bodyJson().extractingPath(order + ".items[0].taobaoUrl").asArray()
                .containsExactly("https://item.taobao.com/item.htm?id=680000002234");
        assertThat(queue).bodyJson().extractingPath(order + ".items[0].skuProperties[0].valueOriginal").asArray()
                .containsExactly("白色");
        assertThat(queue).bodyJson().extractingPath(order + ".items[0].quantity").asArray().containsExactly(1);
        assertThat(queue).bodyJson().extractingPath(order + ".paidMinutesAgo").asArray().hasSize(1);
        assertThat(queue).bodyJson().extractingPath(order + ".paidAt").asArray().hasSize(1);
        assertThat(as(mvc, customer, mvc.get().uri(BASE + "/queue"))).hasStatus(HttpStatus.FORBIDDEN);
    }

    @Test
    void buyerTakesTheOrderAndBuysWithinTheThreshold() {
        String number = paidOrder();
        String id = orderId(mvc, admin, number);

        MvcTestResult started = as(mvc, buyer, mvc.post().uri(BASE + "/orders/{id}/start", id));
        assertThat(started).bodyJson().extractingPath("$.order.status").isEqualTo("PURCHASING");
        assertThat(started).bodyJson().extractingPath("$.buyerId").asString().isNotBlank();
        String item = read(started, "$.order.items[0].id");

        // Another buyer cannot work on it.
        String otherBuyer = TestLogins.staffToken(mvc, "BUYER");
        assertThat(purchased(otherBuyer, item, "89")).bodyJson().extractingPath("$.code")
                .isEqualTo("ORDER_TAKEN_BY_OTHER_BUYER");

        // 91.67 is exactly 3% more than 89 (rounded): not more than the threshold.
        MvcTestResult bought = purchased(buyer, item, "91.67");

        assertThat(bought).hasStatusOk();
        assertThat(bought).bodyJson().extractingPath("$.order.items[0].status").isEqualTo("PURCHASED");
        assertThat(bought).bodyJson().extractingPath("$.order.items[0].taobaoOrderNumber").isEqualTo("TB-1001");
        assertThat(bought).bodyJson().extractingPath("$.order.status").isEqualTo("PURCHASED");
        assertThat(bought).bodyJson().extractingPath("$.order.priceDifferenceUzs").isEqualTo(0);
        assertThat(purchased(buyer, item, "89")).bodyJson().extractingPath("$.code").isEqualTo("ORDER_NOT_PURCHASING");
    }

    @Test
    void priceAboveTheThresholdAsksTheCustomerWhoPaysTheDifference() {
        String number = paidOrder();
        String id = orderId(mvc, admin, number);
        String item = read(as(mvc, buyer, mvc.post().uri(BASE + "/orders/{id}/start", id)), "$.order.items[0].id");

        // 92 CNY is 3.4% more than 89.
        MvcTestResult changed = purchased(buyer, item, "92");
        assertThat(changed).bodyJson().extractingPath("$.order.status").isEqualTo("PRICE_CHANGED");
        assertThat(changed).bodyJson().extractingPath("$.order.items[0].status").isEqualTo("PRICE_CHANGED");

        MvcTestResult accepted = as(mvc, customer, mvc.post().uri("/api/v1/orders/{n}/decision", number)
                .contentType(MediaType.APPLICATION_JSON).content("{\"action\":\"ACCEPT\"}"));

        // 92 CNY: goods 163 800 + fee 16 400 = 180 200 instead of 158 400 + 15 800 = 174 200.
        assertThat(accepted).bodyJson().extractingPath("$.priceDifferenceUzs").isEqualTo(6000);
        assertThat(accepted).bodyJson().extractingPath("$.amountDueUzs").isEqualTo(6000);
        assertThat(accepted).bodyJson().extractingPath("$.items[0].status").isEqualTo("PURCHASED");
        assertThat(accepted).bodyJson().extractingPath("$.status").isEqualTo("PURCHASED");

        // The parcel cannot leave China until the difference is paid.
        assertThat(status(mvc, admin, id, "{\"toStatus\":\"AT_WAREHOUSE\",\"realWeightKg\":0.8}")).hasStatusOk();
        MvcTestResult blocked = as(mvc, admin, mvc.post().uri("/api/v1/admin/orders/{id}/ship", id)
                .contentType(MediaType.APPLICATION_JSON).content("{\"internationalTrackingNumber\":\"CN1\"}"));
        assertThat(blocked).hasStatus(HttpStatus.CONFLICT);
        assertThat(blocked).bodyJson().extractingPath("$.code").isEqualTo("PRICE_DIFFERENCE_UNPAID");

        wallets.credit(customerId, new BigDecimal("6000"), WalletTransactionType.REFUND, null, null, "test money");
        MvcTestResult paid = as(mvc, customer, mvc.post().uri("/api/v1/orders/{n}/pay-price-difference", number)
                .contentType(MediaType.APPLICATION_JSON).content("{\"provider\":\"WALLET\"}"));
        assertThat(paid).bodyJson().extractingPath("$.paid").isEqualTo(true);
        assertThat(paid).bodyJson().extractingPath("$.amountUzs").isEqualTo(6000);

        assertThat(as(mvc, admin, mvc.post().uri("/api/v1/admin/orders/{id}/ship", id)
                .contentType(MediaType.APPLICATION_JSON).content("{\"internationalTrackingNumber\":\"CN1\"}")))
                .bodyJson().extractingPath("$.order.status").isEqualTo("IN_TRANSIT");
    }

    @Test
    void outOfStockItemIsRefundedWhenTheCustomerContinues() {
        String number = twoLineOrderPaid();
        String id = orderId(mvc, admin, number);
        MvcTestResult started = as(mvc, buyer, mvc.post().uri(BASE + "/orders/{id}/start", id));
        String first = itemId(started, "680000002234001");
        String second = itemId(started, "680000002234002");

        assertThat(purchased(buyer, first, "89")).bodyJson().extractingPath("$.order.status").isEqualTo("PURCHASING");
        MvcTestResult missing = as(mvc, buyer, mvc.post().uri(BASE + "/items/{id}/out-of-stock", second)
                .contentType(MediaType.APPLICATION_JSON).content("{\"comment\":\"Sotuvchida tugagan\"}"));
        assertThat(missing).bodyJson().extractingPath("$.order.status").isEqualTo("OUT_OF_STOCK");

        MvcTestResult accepted = as(mvc, customer, mvc.post().uri("/api/v1/orders/{n}/decision", number)
                .contentType(MediaType.APPLICATION_JSON).content("{\"action\":\"ACCEPT\"}"));

        assertThat(accepted).bodyJson().extractingPath("$.status").isEqualTo("PURCHASED");
        assertThat(accepted).bodyJson().extractingPath("$.refundedUzs").isEqualTo(238200);
        assertThat(accepted).bodyJson().extractingPath("$.items[?(@.status == 'OUT_OF_STOCK')].staffComment")
                .asArray().containsExactly("Sotuvchida tugagan");
        assertThat(wallets.balance(customerId)).isEqualByComparingTo("238200");
    }

    @Test
    void refusingTheNewPriceRefundsEverything() {
        String number = paidOrder();
        String id = orderId(mvc, admin, number);
        String item = read(as(mvc, buyer, mvc.post().uri(BASE + "/orders/{id}/start", id)), "$.order.items[0].id");
        purchased(buyer, item, "120");

        as(mvc, customer, mvc.post().uri("/api/v1/orders/{n}/decision", number)
                .contentType(MediaType.APPLICATION_JSON).content("{\"action\":\"REFUND\"}"));

        assertThat(orderStatus(mvc, customer, number)).isEqualTo("CANCELLED");
        assertThat(wallets.balance(customerId)).isEqualByComparingTo("238200");
    }

    @Test
    void onlyBuyersAndAdminsBuy() {
        String id = orderId(mvc, admin, paidOrder());
        String operator = TestLogins.staffToken(mvc, "OPERATOR");

        assertThat(as(mvc, operator, mvc.post().uri(BASE + "/orders/{id}/start", id))).hasStatus(HttpStatus.FORBIDDEN);
        assertThat(as(mvc, admin, mvc.post().uri(BASE + "/orders/{id}/start", id)))
                .bodyJson().extractingPath("$.order.status").isEqualTo("PURCHASING");
    }

    private String paidOrder() {
        String number = newOrder(mvc, customer);
        assertThat(status(mvc, admin, orderId(mvc, admin, number), "{\"toStatus\":\"PAID\"}")).hasStatusOk();
        return number;
    }

    private String twoLineOrderPaid() {
        for (String sku : List.of("680000002234001", "680000002234002")) {
            as(mvc, customer, mvc.post().uri("/api/v1/cart/items").contentType(MediaType.APPLICATION_JSON)
                    .content("{\"externalProductId\":\"680000002234\",\"skuId\":\"%s\"}".formatted(sku)));
        }
        String number = read(as(mvc, customer, mvc.post().uri("/api/v1/orders").contentType(MediaType.APPLICATION_JSON)
                .content("{\"deliveryMethod\":\"CARGO_PICKUP\",\"paymentMethod\":\"PAYME\"}")), "$.number");
        status(mvc, admin, orderId(mvc, admin, number), "{\"toStatus\":\"PAID\"}");
        return number;
    }

    private MvcTestResult purchased(String token, String itemId, String price) {
        return as(mvc, token, mvc.post().uri(BASE + "/items/{id}/purchased", itemId).contentType(MediaType.APPLICATION_JSON)
                .content("{\"actualPriceCny\":%s,\"taobaoOrderNumber\":\"TB-1001\",\"chinaTrackingNumber\":\"SF-1\"}"
                        .formatted(price)));
    }

    private static String itemId(MvcTestResult result, String skuId) {
        List<String> ids = JsonPath.read(text(result), "$.order.items[?(@.skuId == '" + skuId + "')].id");
        return ids.getFirst();
    }

    private static String text(MvcTestResult result) {
        try {
            return result.getResponse().getContentAsString();
        } catch (UnsupportedEncodingException ex) {
            throw new IllegalStateException(ex);
        }
    }
}
