package uz.karvon.notification.api;

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

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static uz.karvon.TestLogins.bearer;
import static uz.karvon.TestLogins.newPhone;
import static uz.karvon.TestLogins.read;
import static uz.karvon.payment.PaymentTestSupport.as;
import static uz.karvon.payment.PaymentTestSupport.newOrder;
import static uz.karvon.payment.PaymentTestSupport.orderId;
import static uz.karvon.payment.PaymentTestSupport.status;

/**
 * SMS turned off (the test default): a customer Telegram cannot reach gets the messages in-app, read through
 * GET /api/v1/me/notifications (newest first, in the site language), the unread count and the read calls.
 */
class InAppNotificationIntegrationTest extends AbstractIntegrationTest {

    private static final String LIST = "/api/v1/me/notifications";

    @Autowired
    private MockMvcTester mvc;

    @Autowired
    private JdbcTemplate jdbc;

    private String admin;
    private String customer;
    private String customerId;

    @BeforeEach
    void customer() {
        admin = TestLogins.adminToken(mvc);
        MvcTestResult login = TestLogins.login(mvc, newPhone());
        customer = read(login, "$.accessToken");
        customerId = read(login, "$.user.id");
    }

    @Test
    void withoutTelegramTheMessagesWaitOnTheSite() {
        unlinkTelegram();
        String number = newOrder(mvc, customer);
        String id = orderId(mvc, admin, number);
        assertThat(unread()).isZero();

        assertThat(status(mvc, admin, id, "{\"toStatus\":\"PAID\"}")).hasStatusOk();
        assertThat(status(mvc, admin, id, "{\"toStatus\":\"PURCHASING\"}")).hasStatusOk();
        assertThat(status(mvc, admin, id, "{\"toStatus\":\"PURCHASED\"}")).hasStatusOk();

        assertThat(jdbc.queryForList("SELECT DISTINCT channel FROM notifications WHERE order_id = ?::uuid",
                String.class, id)).containsExactly("IN_APP");
        assertThat(unread()).isEqualTo(2);

        MvcTestResult list = get(LIST, "uz");
        assertThat(list).hasStatusOk();
        assertThat(list).bodyJson().extractingPath("$.totalElements").isEqualTo(2);
        assertThat(list).bodyJson().extractingPath("$.content[0].text")
                .isEqualTo("E-Market: %s sotib olindi. Omborga kelgach xabar beramiz.".formatted(number));
        assertThat(list).bodyJson().extractingPath("$.content[1].text")
                .isEqualTo("E-Market: %s buyurtmangiz to'landi. Tez orada sotib olamiz.".formatted(number));
        assertThat(list).bodyJson().extractingPath("$.content[0].read").isEqualTo(false);
        assertThat(list).bodyJson().extractingPath("$.content[0].orderId").isEqualTo(id);
        assertThat(get(LIST, "ru")).bodyJson().extractingPath("$.content[1].text")
                .isEqualTo("E-Market: заказ %s оплачен. Скоро выкупим его.".formatted(number));

        // One read, then all.
        String newest = read(list, "$.content[0].id");
        assertThat(post(LIST + "/" + newest + "/read", customer)).hasStatus(HttpStatus.NO_CONTENT);
        assertThat(unread()).isEqualTo(1);
        assertThat(get(LIST, "uz")).bodyJson().extractingPath("$.content[0].read").isEqualTo(true);
        assertThat(post(LIST + "/read", customer)).hasStatus(HttpStatus.NO_CONTENT);
        assertThat(unread()).isZero();
    }

    @Test
    void telegramTurnedOffInTheSettingsAlsoKeepsThemInApp() {
        assertThat(as(mvc, customer, mvc.patch().uri("/api/v1/me/notification-settings")
                .contentType(MediaType.APPLICATION_JSON).content("{\"telegram\":false}"))).hasStatusOk();
        String id = orderId(mvc, admin, newOrder(mvc, customer));

        assertThat(status(mvc, admin, id, "{\"toStatus\":\"PAID\"}")).hasStatusOk();

        assertThat(jdbc.queryForList("SELECT channel FROM notifications WHERE order_id = ?::uuid", String.class, id))
                .containsExactly("IN_APP");
        assertThat(unread()).isEqualTo(1);
    }

    @Test
    void aLinkedCustomerGetsTelegramOnly() {
        String id = orderId(mvc, admin, newOrder(mvc, customer));

        assertThat(status(mvc, admin, id, "{\"toStatus\":\"PAID\"}")).hasStatusOk();

        assertThat(jdbc.queryForList("SELECT channel FROM notifications WHERE order_id = ?::uuid", String.class, id))
                .containsExactly("TELEGRAM");
        assertThat(unread()).isZero();
    }

    @Test
    void anotherUsersNotificationCannotBeRead() {
        unlinkTelegram();
        String id = orderId(mvc, admin, newOrder(mvc, customer));
        status(mvc, admin, id, "{\"toStatus\":\"PAID\"}");
        String notification = read(get(LIST, "uz"), "$.content[0].id");
        String other = read(TestLogins.login(mvc, newPhone()), "$.accessToken");

        MvcTestResult refused = post(LIST + "/" + notification + "/read", other);

        assertThat(refused).hasStatus(HttpStatus.NOT_FOUND);
        assertThat(refused).bodyJson().extractingPath("$.code").isEqualTo("NOTIFICATION_NOT_FOUND");
        assertThat(post(LIST + "/" + UUID.randomUUID() + "/read", customer)).hasStatus(HttpStatus.NOT_FOUND);
        assertThat(unread()).isEqualTo(1);
        assertThat(mvc.get().uri(LIST)).hasStatus(HttpStatus.UNAUTHORIZED);
    }

    /** A customer whose Telegram chat is gone (e.g. linked before, or created by staff). */
    private void unlinkTelegram() {
        jdbc.update("UPDATE users SET telegram_chat_id = NULL WHERE id = ?::uuid", customerId);
    }

    private long unread() {
        return Long.parseLong(read(as(mvc, customer, mvc.get().uri(LIST + "/unread-count")), "$.count"));
    }

    private MvcTestResult get(String uri, String language) {
        return mvc.get().uri(uri).header(HttpHeaders.AUTHORIZATION, bearer(customer))
                .header(HttpHeaders.ACCEPT_LANGUAGE, language).exchange();
    }

    private MvcTestResult post(String uri, String token) {
        return mvc.post().uri(uri).header(HttpHeaders.AUTHORIZATION, bearer(token)).exchange();
    }
}
