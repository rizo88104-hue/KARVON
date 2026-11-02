package uz.karvon.order.api;

import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.assertj.MockMvcTester;
import org.springframework.test.web.servlet.assertj.MvcTestResult;
import uz.karvon.AbstractIntegrationTest;
import uz.karvon.TestLogins;

import java.io.UnsupportedEncodingException;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static uz.karvon.TestLogins.newPhone;
import static uz.karvon.TestLogins.read;
import static uz.karvon.payment.PaymentTestSupport.as;
import static uz.karvon.payment.PaymentTestSupport.newOrder;
import static uz.karvon.payment.PaymentTestSupport.orderId;
import static uz.karvon.payment.PaymentTestSupport.status;

/**
 * Warehouse and shipping steps done by staff. The order (89 CNY, 0.8 kg) prepaid 64 000 UZS of shipping.
 */
class StaffOrderStepsIntegrationTest extends AbstractIntegrationTest {

    private static final String ORDERS = "/api/v1/admin/orders";
    private static final byte[] PNG = {(byte) 0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A, 0, 0, 0, 13};
    private static final byte[] JPEG = {(byte) 0xFF, (byte) 0xD8, (byte) 0xFF, (byte) 0xE0, 0, 16};

    @Autowired
    private MockMvcTester mvc;

    @Autowired
    private JdbcTemplate jdbc;

    private String customer;
    private String admin;
    private String buyer;
    private String operator;

    @BeforeEach
    void users() {
        customer = TestLogins.accessToken(mvc, newPhone());
        admin = TestLogins.adminToken(mvc);
        buyer = TestLogins.staffToken(mvc, "BUYER");
        operator = TestLogins.staffToken(mvc, "OPERATOR");
    }

    @Test
    void weighPhotographShipArriveDeliver() {
        String number = newOrder(mvc, customer);
        String id = purchased(number);

        MvcTestResult weighed = post(buyer, ORDERS + "/{id}/weight", id, "{\"realWeightKg\":1.2,\"comment\":\"Qutida\"}");
        assertThat(weighed).hasStatusOk();
        assertThat(weighed).bodyJson().extractingPath("$.order.status").isEqualTo("AWAITING_SHIPPING_PAYMENT");
        assertThat(weighed).bodyJson().extractingPath("$.order.finalShippingUzs").isEqualTo(96000);

        MvcTestResult photos = upload(buyer, id, new MockMultipartFile("files", "a.png", "image/png", PNG),
                new MockMultipartFile("files", "b.jpg", "image/jpeg", JPEG));
        assertThat(photos).hasStatusOk();
        assertThat(photos).bodyJson().extractingPath("$.length()").isEqualTo(2);

        // The customer sees the photos and can open them.
        MvcTestResult seen = as(mvc, customer, mvc.get().uri("/api/v1/orders/{n}", number));
        assertThat(seen).bodyJson().extractingPath("$.photos.length()").isEqualTo(2);
        String url = read(seen, "$.photos[0].url");
        MvcTestResult file = mvc.get().uri(url.substring("http://localhost:8080".length())).exchange();
        assertThat(file).hasStatusOk();
        assertThat(file.getResponse().getContentAsByteArray()).isEqualTo(PNG);

        // Shipping is refused until the 32 000 difference is paid.
        String ship = "{\"internationalTrackingNumber\":\"CN123UZ\",\"carrier\":\"Taobao Global\",\"expectedArrivalDate\":\"2026-11-25\"}";
        MvcTestResult blocked = post(operator, ORDERS + "/{id}/ship", id, ship);
        assertThat(blocked).hasStatus(HttpStatus.CONFLICT);
        assertThat(blocked).bodyJson().extractingPath("$.code").isEqualTo("SHIPPING_DIFFERENCE_UNPAID");
        as(mvc, admin, mvc.post().uri(ORDERS + "/{id}/shipping-payment", id));

        MvcTestResult shipped = post(operator, ORDERS + "/{id}/ship", id, ship);
        assertThat(shipped).bodyJson().extractingPath("$.order.status").isEqualTo("IN_TRANSIT");
        assertThat(shipped).bodyJson().extractingPath("$.order.carrier").isEqualTo("Taobao Global");
        assertThat(shipped).bodyJson().extractingPath("$.order.expectedArrivalDate").isEqualTo("2026-11-25");
        assertThat(shipped).bodyJson().extractingPath("$.order.internationalTrackingNumber").isEqualTo("CN123UZ");

        MvcTestResult arrived = post(operator, ORDERS + "/{id}/arrived", id,
                "{\"pickupInfo\":\"Chilonzor 9, kargo ofisi, 10:00-19:00\"}");
        assertThat(arrived).bodyJson().extractingPath("$.order.pickupInfo").isEqualTo("Chilonzor 9, kargo ofisi, 10:00-19:00");
        assertThat(post(operator, ORDERS + "/{id}/delivered", id, "{}"))
                .bodyJson().extractingPath("$.order.status").isEqualTo("DELIVERED");
    }

    @Test
    void photoRules() {
        String id = purchased(newOrder(mvc, customer));

        assertThat(upload(buyer, id, new MockMultipartFile("files", "x.png", "image/png", "not an image".getBytes())))
                .bodyJson().extractingPath("$.code").isEqualTo("PHOTO_TYPE_NOT_ALLOWED");
        MockMultipartFile png = new MockMultipartFile("files", "a.png", "image/png", PNG);
        assertThat(upload(buyer, id, png, png, png, png)).hasStatusOk();
        assertThat(upload(buyer, id, png)).bodyJson().extractingPath("$.code").isEqualTo("PHOTO_LIMIT");

        String photo = read(as(mvc, admin, mvc.get().uri(ORDERS + "/{id}", id)), "$.order.photos[0].id");
        assertThat(as(mvc, buyer, mvc.delete().uri(ORDERS + "/{id}/photos/{p}", id, photo)))
                .bodyJson().extractingPath("$.length()").isEqualTo(3);

        String unpaid = orderId(mvc, admin, newOrder(mvc, customer));
        assertThat(upload(buyer, unpaid, png)).bodyJson().extractingPath("$.code").isEqualTo("PHOTO_NOT_ALLOWED_NOW");
        assertThat(upload(customer, id, png)).hasStatus(HttpStatus.FORBIDDEN);
    }

    @Test
    void rolesOfTheSteps() {
        String id = purchased(newOrder(mvc, customer));

        assertThat(post(operator, ORDERS + "/{id}/weight", id, "{\"realWeightKg\":0.8}"))
                .bodyJson().extractingPath("$.order.status").isEqualTo("AT_WAREHOUSE");
        assertThat(post(buyer, ORDERS + "/{id}/ship", id, "{\"internationalTrackingNumber\":\"CN1\"}"))
                .hasStatus(HttpStatus.FORBIDDEN);
        assertThat(post(operator, ORDERS + "/{id}/delivered", id, "{}"))
                .bodyJson().extractingPath("$.code").isEqualTo("INVALID_STATUS_TRANSITION");
        assertThat(post(operator, ORDERS + "/{id}/ship", id, "{\"carrier\":\"x\"}")).hasStatus(HttpStatus.BAD_REQUEST);
    }

    @Test
    void bulkStatusMovesWhatCanMoveAndReportsTheRest() {
        String first = weighed(newOrder(mvc, customer), "0.8");
        String second = weighed(newOrder(mvc, customer), "0.6");
        String unpaidDifference = weighed(newOrder(mvc, customer), "1.5");

        MvcTestResult result = post(operator, ORDERS + "/bulk-status", null,
                "{\"orderIds\":[\"%s\",\"%s\",\"%s\"],\"toStatus\":\"IN_TRANSIT\",\"trackingNumber\":\"CARGO-77\"}"
                        .formatted(first, second, unpaidDifference));

        assertThat(result).hasStatusOk();
        assertThat(result).bodyJson().extractingPath("$[?(@.changed == true)].orderId").asArray()
                .containsExactlyInAnyOrder(first, second);
        assertThat(result).bodyJson().extractingPath("$[?(@.orderId == '%s')].errorCode".formatted(unpaidDifference))
                .asArray().containsExactly("SHIPPING_DIFFERENCE_UNPAID");
        assertThat(as(mvc, admin, mvc.get().uri(ORDERS + "/{id}", first)))
                .bodyJson().extractingPath("$.order.internationalTrackingNumber").isEqualTo("CARGO-77");

        assertThat(post(operator, ORDERS + "/bulk-status", null,
                "{\"orderIds\":[\"%s\",\"%s\"],\"toStatus\":\"ARRIVED\"}".formatted(first, second)))
                .bodyJson().extractingPath("$[*].status").asArray().containsOnly("ARRIVED");
        assertThat(post(operator, ORDERS + "/bulk-status", null,
                "{\"orderIds\":[\"%s\"],\"toStatus\":\"PAID\"}".formatted(first)))
                .bodyJson().extractingPath("$.code").isEqualTo("BULK_STATUS_NOT_ALLOWED");
    }

    @Test
    void delayedListsOrdersOverTheirSlaLimit() {
        String late = orderId(mvc, admin, newOrder(mvc, customer));
        String onTime = orderId(mvc, admin, newOrder(mvc, customer));
        status(mvc, admin, late, "{\"toStatus\":\"PAID\"}");
        status(mvc, admin, onTime, "{\"toStatus\":\"PAID\"}");
        // Paid 50 hours ago; the PAID limit is 48 hours.
        jdbc.update("UPDATE order_status_history SET created_at = now() - interval '50 hours' "
                + "WHERE order_id = ?::uuid AND to_status = 'PAID'", late);

        MvcTestResult delayed = as(mvc, operator, mvc.get().uri(ORDERS + "/delayed"));

        assertThat(delayed).hasStatusOk();
        List<String> ids = JsonPath.read(text(delayed), "$[*].orderId");
        assertThat(ids).contains(late).doesNotContain(onTime);
        assertThat(delayed).bodyJson().extractingPath("$[?(@.orderId == '%s')].limitHours".formatted(late))
                .asArray().containsExactly(48);
        assertThat(delayed).bodyJson().extractingPath("$[?(@.orderId == '%s')].overdueHours".formatted(late))
                .asArray().containsExactly(2);

        // A stricter limit set by ADMIN applies at once; the old one is restored afterwards.
        String defaults = "{\"limits\":{\"PAID\":48,\"PURCHASED\":240,\"IN_TRANSIT\":600}}";
        try {
            assertThat(put(admin, "/api/v1/admin/settings/sla", "{\"limits\":{\"PAID\":1}}"))
                    .bodyJson().extractingPath("$.limits.PAID").isEqualTo(1);
            assertThat(as(mvc, admin, mvc.get().uri("/api/v1/admin/settings/sla/history")))
                    .bodyJson().extractingPath("$.content[0].limits.PAID").isEqualTo(1);
            List<String> stricter = JsonPath.read(text(as(mvc, operator, mvc.get().uri(ORDERS + "/delayed"))), "$[*].orderId");
            assertThat(stricter).contains(late);
            assertThat(put(operator, "/api/v1/admin/settings/sla", defaults)).hasStatus(HttpStatus.FORBIDDEN);
            assertThat(put(admin, "/api/v1/admin/settings/sla", "{\"limits\":{\"paid\":0}}"))
                    .hasStatus(HttpStatus.BAD_REQUEST);
        } finally {
            assertThat(put(admin, "/api/v1/admin/settings/sla", defaults)).hasStatusOk();
        }
    }

    /** Paid and bought (by the admin, for speed). */
    private String purchased(String number) {
        String id = orderId(mvc, admin, number);
        status(mvc, admin, id, "{\"toStatus\":\"PAID\"}");
        status(mvc, admin, id, "{\"toStatus\":\"PURCHASING\"}");
        status(mvc, admin, id, "{\"toStatus\":\"PURCHASED\"}");
        return id;
    }

    private String weighed(String number, String weight) {
        String id = purchased(number);
        assertThat(post(operator, ORDERS + "/{id}/weight", id, "{\"realWeightKg\":%s}".formatted(weight))).hasStatusOk();
        return id;
    }

    private MvcTestResult upload(String token, String orderId, MockMultipartFile... files) {
        var request = mvc.post().uri(ORDERS + "/{id}/photos", orderId).multipart();
        for (MockMultipartFile file : files) {
            request.file(file);
        }
        return request.header(HttpHeaders.AUTHORIZATION, TestLogins.bearer(token)).exchange();
    }

    private MvcTestResult post(String token, String uri, String id, String body) {
        var request = id == null ? mvc.post().uri(uri) : mvc.post().uri(uri, id);
        return as(mvc, token, request.contentType(MediaType.APPLICATION_JSON).content(body));
    }

    private MvcTestResult put(String token, String uri, String body) {
        return as(mvc, token, mvc.put().uri(uri).contentType(MediaType.APPLICATION_JSON).content(body));
    }

    private static String text(MvcTestResult result) {
        try {
            return result.getResponse().getContentAsString();
        } catch (UnsupportedEncodingException ex) {
            throw new IllegalStateException(ex);
        }
    }
}
