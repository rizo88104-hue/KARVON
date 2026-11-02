package uz.karvon.notification.api;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.assertj.MockMvcTester;
import org.springframework.test.web.servlet.assertj.MvcTestResult;
import org.springframework.transaction.support.TransactionTemplate;
import uz.karvon.AbstractIntegrationTest;
import uz.karvon.TestLogins;
import uz.karvon.notification.application.NotificationRetryJob;
import uz.karvon.notification.infrastructure.FakeSmsSender;
import uz.karvon.notification.infrastructure.FakeTelegramSender;
import uz.karvon.order.application.OrderStatusChangedEvent;
import uz.karvon.order.domain.Actor;
import uz.karvon.order.domain.OrderStatus;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

import static org.assertj.core.api.Assertions.assertThat;
import static uz.karvon.TestLogins.login;
import static uz.karvon.TestLogins.newPhone;
import static uz.karvon.TestLogins.read;
import static uz.karvon.payment.PaymentTestSupport.as;
import static uz.karvon.payment.PaymentTestSupport.newOrder;
import static uz.karvon.payment.PaymentTestSupport.orderId;
import static uz.karvon.payment.PaymentTestSupport.status;

/**
 * Notifications in the test profile are sent in the calling thread right after the commit, by the fake senders.
 */
class NotificationIntegrationTest extends AbstractIntegrationTest {

    private static final String WEBHOOK = "/api/v1/telegram/webhook";
    private static final String SECRET = "test-telegram-secret";

    @Autowired
    private MockMvcTester mvc;

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private FakeSmsSender sms;

    @Autowired
    private FakeTelegramSender telegram;

    @Autowired
    private ApplicationEventPublisher events;

    @Autowired
    private TransactionTemplate transactions;

    @Autowired
    private NotificationRetryJob retryJob;

    private String admin;
    private String phone;
    private String customer;
    private UUID customerId;

    @BeforeEach
    void customer() {
        admin = TestLogins.adminToken(mvc);
        phone = newPhone();
        MvcTestResult login = login(mvc, phone);
        customer = read(login, "$.accessToken");
        customerId = UUID.fromString(read(login, "$.user.id"));
        telegram.failNext(0);
    }

    @Test
    void paidOrderIsAnnouncedBySmsInTheCustomersLanguage() {
        assertThat(as(mvc, customer, mvc.patch().uri("/api/v1/me")
                .contentType(MediaType.APPLICATION_JSON).content("{\"language\":\"RU\"}"))).hasStatusOk();
        String number = newOrder(mvc, customer);

        status(mvc, admin, orderId(mvc, admin, number), "{\"toStatus\":\"PAID\"}");

        assertThat(sms.messages(phone)).contains("Karvon: заказ %s оплачен. Скоро мы его выкупим.".formatted(number));
        List<Map<String, Object>> rows = rows();
        assertThat(rows).hasSize(1);
        assertThat(rows.getFirst()).containsEntry("channel", "SMS").containsEntry("status", "SENT")
                .containsEntry("language", "RU").containsEntry("template_key", "order.paid")
                .containsEntry("recipient", phone).containsEntry("attempts", 1);
    }

    @Test
    void linkedTelegramGetsEveryMessageAndSmsOnlyTheImportantOnes() {
        long chat = linkTelegram();
        assertThat(as(mvc, customer, mvc.get().uri("/api/v1/me/notification-settings")))
                .bodyJson().extractingPath("$.telegramLinked").isEqualTo(true);
        String number = newOrder(mvc, customer);
        String id = orderId(mvc, admin, number);

        status(mvc, admin, id, "{\"toStatus\":\"PAID\"}");
        status(mvc, admin, id, "{\"toStatus\":\"PURCHASING\"}");
        status(mvc, admin, id, "{\"toStatus\":\"PURCHASED\"}");

        String paid = "Karvon: %s buyurtmangiz to'landi. Tez orada sotib olamiz.".formatted(number);
        String purchased = "Karvon: %s sotib olindi. Omborga kelgach xabar beramiz.".formatted(number);
        assertThat(telegram.messages(chat)).contains(paid, purchased);
        assertThat(sms.messages(phone)).contains(paid).doesNotContain(purchased);
    }

    @Test
    void webhookNeedsTheSecretAndALinkWorksOnce() {
        String token = linkToken();
        long chat = newChat();

        assertThat(mvc.post().uri(WEBHOOK).contentType(MediaType.APPLICATION_JSON).content(start(chat, token)).exchange())
                .hasStatus(HttpStatus.UNAUTHORIZED).bodyJson().extractingPath("$.code")
                .isEqualTo("TELEGRAM_WEBHOOK_UNAUTHORIZED");
        assertThat(webhook("wrong-secret", start(chat, token))).hasStatus(HttpStatus.UNAUTHORIZED);
        assertThat(telegram.messages(chat)).isEmpty();

        assertThat(webhook(SECRET, start(chat, token))).hasStatusOk();
        assertThat(telegram.messages(chat)).last().asString().startsWith("Karvon: Telegram ulandi.");

        long otherChat = newChat();
        assertThat(webhook(SECRET, start(otherChat, token))).hasStatusOk();
        assertThat(telegram.messages(otherChat)).containsExactly(
                "Karvon: havola eskirgan yoki ishlatilgan. Kabinetdan yangi havola oling.");
        assertThat(jdbc.queryForObject("SELECT telegram_chat_id FROM users WHERE id = ?", Long.class, customerId))
                .isEqualTo(chat);
        // other updates are accepted and ignored
        assertThat(webhook(SECRET, "{\"update_id\":5,\"edited_message\":{}}")).hasStatusOk();
    }

    @Test
    void nothingIsSentWhenTheTransactionRollsBack() {
        transactions.executeWithoutResult(tx -> {
            events.publishEvent(paidEvent("K-ROLLBACK"));
            tx.setRollbackOnly();
        });

        assertThat(rows()).isEmpty();
        assertThat(sms.messages(phone)).noneMatch(text -> text.contains("K-ROLLBACK"));

        transactions.executeWithoutResult(tx -> events.publishEvent(paidEvent("K-COMMIT")));

        assertThat(rows()).hasSize(1);
        assertThat(sms.messages(phone)).anyMatch(text -> text.contains("K-COMMIT"));
    }

    @Test
    void failedSendIsRetriedByTheJobWhenItsTimeComes() {
        long chat = linkTelegram();
        telegram.failNext(1);

        transactions.executeWithoutResult(tx -> events.publishEvent(purchasedEvent("K-RETRY")));

        Map<String, Object> failed = single();
        assertThat(failed).containsEntry("channel", "TELEGRAM").containsEntry("status", "FAILED")
                .containsEntry("attempts", 1);
        assertThat((String) failed.get("last_error")).contains("Fake failure");
        assertThat(((java.sql.Timestamp) failed.get("next_attempt_at")).toInstant()).isAfter(Instant.now());

        retryJob.retryDue();
        assertThat(single()).containsEntry("status", "FAILED");

        jdbc.update("UPDATE notifications SET next_attempt_at = now() - interval '1 second' WHERE user_id = ?", customerId);
        retryJob.retryDue();

        assertThat(single()).containsEntry("status", "SENT").containsEntry("attempts", 2);
        assertThat(telegram.messages(chat)).contains("Karvon: K-RETRY sotib olindi. Omborga kelgach xabar beramiz.");
    }

    @Test
    void adminListsFailedNotificationsAndRetriesThem() {
        linkTelegram();
        telegram.failNext(1);
        transactions.executeWithoutResult(tx -> events.publishEvent(purchasedEvent("K-ADMIN")));
        String id = single().get("id").toString();

        MvcTestResult failed = as(mvc, admin, mvc.get().uri("/api/v1/admin/notifications?status=FAILED&size=100"));
        assertThat(failed).hasStatusOk();
        assertThat(failed).bodyJson().extractingPath("$.content[?(@.id == '%s')].text".formatted(id)).asArray()
                .containsExactly("Karvon: K-ADMIN sotib olindi. Omborga kelgach xabar beramiz.");
        assertThat(as(mvc, customer, mvc.get().uri("/api/v1/admin/notifications"))).hasStatus(HttpStatus.FORBIDDEN);

        MvcTestResult retried = as(mvc, admin, mvc.post().uri("/api/v1/admin/notifications/{id}/retry", id));
        assertThat(retried).hasStatusOk();
        assertThat(retried).bodyJson().extractingPath("$.status").isEqualTo("SENT");
        assertThat(retried).bodyJson().extractingPath("$.attempts").isEqualTo(2);

        assertThat(as(mvc, admin, mvc.post().uri("/api/v1/admin/notifications/{id}/retry", id)))
                .hasStatus(HttpStatus.CONFLICT).bodyJson().extractingPath("$.code").isEqualTo("NOTIFICATION_ALREADY_SENT");
        assertThat(as(mvc, admin, mvc.post().uri("/api/v1/admin/notifications/{id}/retry", UUID.randomUUID())))
                .hasStatus(HttpStatus.NOT_FOUND);
    }

    @Test
    void customerCanTurnSmsOff() {
        MvcTestResult changed = as(mvc, customer, mvc.patch().uri("/api/v1/me/notification-settings")
                .contentType(MediaType.APPLICATION_JSON).content("{\"sms\":false}"));
        assertThat(changed).hasStatusOk();
        assertThat(changed).bodyJson().isEqualTo("{\"sms\":false,\"telegram\":true,\"telegramLinked\":false}");

        transactions.executeWithoutResult(tx -> events.publishEvent(paidEvent("K-SILENT")));

        assertThat(rows()).isEmpty();
    }

    /** Links a new Telegram chat to the customer through the deep link and the webhook; returns the chat id. */
    private long linkTelegram() {
        long chat = newChat();
        assertThat(webhook(SECRET, start(chat, linkToken()))).hasStatusOk();
        return chat;
    }

    private String linkToken() {
        MvcTestResult link = as(mvc, customer, mvc.get().uri("/api/v1/me/telegram-link"));
        assertThat(link).hasStatusOk();
        String url = read(link, "$.url");
        assertThat(url).startsWith("https://t.me/karvon_test_bot?start=");
        return url.substring(url.indexOf("start=") + "start=".length());
    }

    private MvcTestResult webhook(String secret, String body) {
        return mvc.post().uri(WEBHOOK).header("X-Telegram-Bot-Api-Secret-Token", secret)
                .contentType(MediaType.APPLICATION_JSON).content(body).exchange();
    }

    private static String start(long chat, String token) {
        return "{\"update_id\":1,\"message\":{\"message_id\":1,\"chat\":{\"id\":%d,\"type\":\"private\"},\"text\":\"/start %s\"}}"
                .formatted(chat, token);
    }

    private static long newChat() {
        return ThreadLocalRandom.current().nextLong(1_000_000, Long.MAX_VALUE / 2);
    }

    private OrderStatusChangedEvent paidEvent(String number) {
        return new OrderStatusChangedEvent(UUID.randomUUID(), number, customerId, OrderStatus.AWAITING_PAYMENT,
                OrderStatus.PAID, Actor.SYSTEM, Instant.now());
    }

    private OrderStatusChangedEvent purchasedEvent(String number) {
        return new OrderStatusChangedEvent(UUID.randomUUID(), number, customerId, OrderStatus.PURCHASING,
                OrderStatus.PURCHASED, Actor.SYSTEM, Instant.now());
    }

    private List<Map<String, Object>> rows() {
        return jdbc.queryForList("SELECT * FROM notifications WHERE user_id = ? ORDER BY created_at", customerId);
    }

    private Map<String, Object> single() {
        List<Map<String, Object>> rows = rows();
        assertThat(rows).hasSize(1);
        return rows.getFirst();
    }
}
