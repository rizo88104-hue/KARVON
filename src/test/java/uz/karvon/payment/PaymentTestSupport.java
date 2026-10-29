package uz.karvon.payment;

import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.assertj.MockMvcTester;
import org.springframework.test.web.servlet.assertj.MvcTestResult;

import static org.assertj.core.api.Assertions.assertThat;
import static uz.karvon.TestLogins.bearer;
import static uz.karvon.TestLogins.read;

/**
 * Orders for payment tests: one line of 女士小白鞋 SKU 001 (89 CNY, 0.8 kg) = 238 200 UZS, cargo pickup.
 */
public final class PaymentTestSupport {

    public static final String SKU = "680000002234001";
    public static final long ORDER_TOTAL = 238_200;

    private PaymentTestSupport() {
    }

    /** A new order waiting for payment; returns its number. */
    public static String newOrder(MockMvcTester mvc, String customer) {
        MvcTestResult cart = as(mvc, customer, mvc.post().uri("/api/v1/cart/items").contentType(MediaType.APPLICATION_JSON)
                .content("{\"externalProductId\":\"%s\",\"skuId\":\"%s\"}".formatted(SKU.substring(0, 12), SKU)));
        assertThat(cart).hasStatus(HttpStatus.CREATED);
        MvcTestResult order = as(mvc, customer, mvc.post().uri("/api/v1/orders").contentType(MediaType.APPLICATION_JSON)
                .content("{\"deliveryMethod\":\"CARGO_PICKUP\",\"paymentMethod\":\"PAYME\"}"));
        assertThat(order).hasStatus(HttpStatus.CREATED);
        return read(order, "$.number");
    }

    public static String orderId(MockMvcTester mvc, String admin, String number) {
        return read(as(mvc, admin, mvc.get().uri("/api/v1/admin/orders?number={n}", number)), "$.content[0].id");
    }

    public static String orderStatus(MockMvcTester mvc, String customer, String number) {
        return read(as(mvc, customer, mvc.get().uri("/api/v1/orders/{n}", number)), "$.status");
    }

    public static MvcTestResult status(MockMvcTester mvc, String admin, String orderId, String body) {
        return as(mvc, admin, mvc.post().uri("/api/v1/admin/orders/{id}/status", orderId)
                .contentType(MediaType.APPLICATION_JSON).content(body));
    }

    /** PAID (by admin) -> PURCHASING -> PURCHASED -> weighed; 1.2 kg makes it AWAITING_SHIPPING_PAYMENT (32 000 due). */
    public static void toWarehouse(MockMvcTester mvc, String admin, String orderId, String realWeightKg, boolean markPaid) {
        if (markPaid) {
            assertThat(status(mvc, admin, orderId, "{\"toStatus\":\"PAID\"}")).hasStatusOk();
        }
        assertThat(status(mvc, admin, orderId, "{\"toStatus\":\"PURCHASING\"}")).hasStatusOk();
        assertThat(status(mvc, admin, orderId, "{\"toStatus\":\"PURCHASED\"}")).hasStatusOk();
        assertThat(status(mvc, admin, orderId, "{\"toStatus\":\"AT_WAREHOUSE\",\"realWeightKg\":%s}".formatted(realWeightKg)))
                .hasStatusOk();
    }

    public static MvcTestResult as(MockMvcTester mvc, String token, MockMvcTester.MockMvcRequestBuilder request) {
        return request.header(HttpHeaders.AUTHORIZATION, bearer(token)).exchange();
    }
}
