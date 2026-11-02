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
import uz.karvon.notification.domain.Notification;
import uz.karvon.notification.domain.NotificationChannel;
import uz.karvon.notification.domain.NotificationStatus;
import uz.karvon.notification.infrastructure.FakeSmsSender;
import uz.karvon.notification.infrastructure.FakeTelegramBotClient;
import uz.karvon.notification.infrastructure.NotificationRepository;
import uz.karvon.order.application.OrderStatusChangedEvent;
import uz.karvon.order.domain.Actor;
import uz.karvon.order.domain.OrderStatus;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;
import java.util.function.BooleanSupplier;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.fail;
import static uz.karvon.TestLogins.newPhone;
import static uz.karvon.TestLogins.read;
import static uz.karvon.payment.PaymentTestSupport.as;
import static uz.karvon.payment.PaymentTestSupport.newOrder;
import static uz.karvon.payment.PaymentTestSupport.orderId;
import static uz.karvon.payment.PaymentTestSupport.status;

/**
 * Orders moved through their statuses by an admin; the fake SMS sender and Telegram bot record what was "sent".
 * The test order (89 CNY, 0.8 kg) weighed at 1.2 kg owes 32 000 UZS of shipping.
 */
class NotificationIntegrationTest extends AbstractIntegrationTest {

    private static final String ORDERS = "/api/v1/admin/orders";
    private static final String WEBHOOK = "/api/v1/telegram/webhook";
    private static final String SECRET = "test-telegram-secret";

    @Autowired
    private MockMvcTester mvc;

    @Autowired
    private FakeSmsSender sms;

    @Autowired
    private FakeTelegramBotClient telegram;

    @Autowired
    private NotificationRepository notifications;

    @Autowired
    private NotificationRetryJob retryJob;

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private TransactionTemplate transactions;

    @Autowired
    private ApplicationEventPublisher events;

    private String admin;
    private String phone;
    private String customer;
    private UUID customerId;

    @BeforeEach
    void users() {
        admin = TestLogins.adminToken(mvc);
        phone = newPhone();
        MvcTestResult login = TestLogins.login(mvc, phone);
        customer = read(login, "$.accessToken");
        customerId = UUID.fromString(read(login, "$.user.id"));
    }

    @Test
    void withoutTelegramEveryStepComesBySmsInUzbek() {
        String number = newOrder(mvc, customer);
        String id = orderId(mvc, admin, number);

        assertThat(status(mvc, admin, id, "{\"toStatus\":\"PAID\"}")).hasStatusOk();
        assertThat(status(mvc, admin, id, "{\"toStatus\":\"PURCHASING\"}")).hasStatusOk();
        assertThat(status(mvc, admin, id, "{\"toStatus\":\"PURCHASED\"}")).hasStatusOk();
        assertThat(post(admin, ORDERS + "/{id}/weight", id, "{\"realWeightKg\":1.2}")).hasStatusOk();
        assertThat(as(mvc, admin, mvc.post().uri(ORDERS + "/{id}/shipping-payment", id))).hasStatusOk();
        assertThat(post(admin, ORDERS + "/{id}/ship", id, "{\"internationalTrackingNumber\":\"CN777UZ\"}")).hasStatusOk();
        assertThat(post(admin, ORDERS + "/{id}/arrived", id, "{\"pickupInfo\":\"Chilonzor 9, kargo ofisi\"}"))
                .hasStatusOk();

        List<String> expected = List.of(
                "Karvon: %s buyurtmangiz to'landi. Tez orada sotib olamiz.".formatted(number),
                "Karvon: %s sotib olindi. Omborga kelgach xabar beramiz.".formatted(number),
                "Karvon: %s omborga keldi, vazni 1.2 kg.".formatted(number),
                "Karvon: %s uchun yetkazish farqi 32 000 so'm. Kabinetda to'lang.".formatted(number),
                "Karvon: %s yo'lga chiqdi. Trek: CN777UZ.".formatted(number),
                "Karvon: %s O'zbekistonga keldi. Chilonzor 9, kargo ofisi".formatted(number));
        await(() -> sms.messages(phone).containsAll(expected));
        await(() -> sentCount() == expected.size());
        assertThat(notifications.findByUserIdOrderByCreatedAtAsc(customerId))
                .extracting(Notification::getChannel).containsOnly(NotificationChannel.SMS);
    }

    @Test
    void linkedTelegramGetsEverythingAndSmsOnlyTheImportantOnes() {
        long chatId = ThreadLocalRandom.current().nextLong(1_000_000, 9_000_000_000L);
        String url = read(as(mvc, customer, mvc.get().uri("/api/v1/me/telegram-link")), "$.url");
        assertThat(url).startsWith("https://t.me/karvon_test_bot?start=");
        String start = "/start " + url.substring(url.indexOf("start=") + "start=".length());

        assertThat(webhook(null, chatId, start)).hasStatus(HttpStatus.FORBIDDEN);
        assertThat(webhook("wrong", chatId, start)).bodyJson().extractingPath("$.code")
                .isEqualTo("TELEGRAM_WEBHOOK_FORBIDDEN");
        assertThat(webhook(SECRET, chatId, start)).hasStatusOk();
        assertThat(telegram.messages(chatId)).containsExactly(
                "Karvon: Telegram ulandi. Buyurtmalaringiz haqidagi xabarlar shu yerga keladi.");
        assertThat(as(mvc, customer, mvc.get().uri("/api/v1/me/notification-settings")))
                .bodyJson().extractingPath("$.telegramLinked").isEqualTo(true);
        // The link works once.
        assertThat(webhook(SECRET, chatId, start)).hasStatusOk();
        assertThat(telegram.messages(chatId)).last().asString().contains("havola eskirgan");

        String number = newOrder(mvc, customer);
        String id = orderId(mvc, admin, number);
        status(mvc, admin, id, "{\"toStatus\":\"PAID\"}");
        status(mvc, admin, id, "{\"toStatus\":\"PURCHASING\"}");
        status(mvc, admin, id, "{\"toStatus\":\"PURCHASED\"}");

        String paid = "Karvon: %s buyurtmangiz to'landi. Tez orada sotib olamiz.".formatted(number);
        String purchased = "Karvon: %s sotib olindi. Omborga kelgach xabar beramiz.".formatted(number);
        await(() -> telegram.messages(chatId).containsAll(List.of(paid, purchased)));
        await(() -> sentCount() == 3);
        // PAID is important: SMS too; PURCHASED is not.
        assertThat(sms.messages(phone)).contains(paid).doesNotContain(purchased);
    }

    @Test
    void textsFollowTheUsersLanguage() {
        assertThat(as(mvc, customer, mvc.patch().uri("/api/v1/me").contentType(MediaType.APPLICATION_JSON)
                .content("{\"language\":\"RU\"}"))).hasStatusOk();
        String number = newOrder(mvc, customer);

        status(mvc, admin, orderId(mvc, admin, number), "{\"toStatus\":\"PAID\"}");

        await(() -> sms.messages(phone).contains("Karvon: заказ %s оплачен. Скоро выкупим его.".formatted(number)));
    }

    @Test
    void channelsTurnedOffAreNotUsed() {
        MvcTestResult updated = as(mvc, customer, mvc.patch().uri("/api/v1/me/notification-settings")
                .contentType(MediaType.APPLICATION_JSON).content("{\"sms\":false}"));
        assertThat(updated).hasStatusOk();
        assertThat(updated).bodyJson().extractingPath("$.sms").isEqualTo(false);
        assertThat(updated).bodyJson().extractingPath("$.telegram").isEqualTo(true);
        assertThat(updated).bodyJson().extractingPath("$.telegramLinked").isEqualTo(false);

        status(mvc, admin, orderId(mvc, admin, newOrder(mvc, customer)), "{\"toStatus\":\"PAID\"}");

        assertThat(notifications.findByUserIdOrderByCreatedAtAsc(customerId)).isEmpty();
    }

    @Test
    void failedSendIsRetriedByTheJobAndByTheAdmin() {
        sms.failNext(phone, 1);
        String number = newOrder(mvc, customer);
        String id = orderId(mvc, admin, number);
        status(mvc, admin, id, "{\"toStatus\":\"PAID\"}");

        UUID failed = awaitOne(NotificationStatus.FAILED);
        assertThat(notifications.findById(failed).orElseThrow())
                .satisfies(n -> assertThat(n.getAttempts()).isEqualTo(1))
                .satisfies(n -> assertThat(n.getLastError()).contains("Fake SMS failure"))
                .satisfies(n -> assertThat(n.getNextAttemptAt()).isAfter(Instant.now()));
        MvcTestResult list = as(mvc, admin, mvc.get().uri("/api/v1/admin/notifications?status=FAILED&size=100"));
        assertThat(list).hasStatusOk();
        assertThat(list).bodyJson().extractingPath("$.content[?(@.id == '%s')].templateKey".formatted(failed))
                .asArray().containsExactly("order.paid");

        // Not due yet: the job leaves it alone; once due, the job sends it.
        retryJob.retryDue();
        assertThat(notifications.findById(failed).orElseThrow().getStatus()).isEqualTo(NotificationStatus.FAILED);
        jdbc.update("UPDATE notifications SET next_attempt_at = now() - interval '1 minute' WHERE id = ?", failed);
        retryJob.retryDue();
        assertThat(notifications.findById(failed).orElseThrow())
                .satisfies(n -> assertThat(n.getStatus()).isEqualTo(NotificationStatus.SENT))
                .satisfies(n -> assertThat(n.getAttempts()).isEqualTo(2));
        assertThat(sms.messages(phone)).contains("Karvon: %s buyurtmangiz to'landi. Tez orada sotib olamiz.".formatted(number));

        // Out of automatic retries: only the admin can send it again.
        sms.failNext(phone, 10);
        status(mvc, admin, id, "{\"toStatus\":\"PURCHASING\"}");
        status(mvc, admin, id, "{\"toStatus\":\"PURCHASED\"}");
        UUID givenUp = awaitOne(NotificationStatus.FAILED);
        jdbc.update("UPDATE notifications SET attempts = 6, next_attempt_at = now() - interval '1 minute' WHERE id = ?",
                givenUp);
        retryJob.retryDue();
        assertThat(notifications.findById(givenUp).orElseThrow().getAttempts()).isEqualTo(6);

        sms.failNext(phone, 0);
        MvcTestResult retried = as(mvc, admin, mvc.post().uri("/api/v1/admin/notifications/{id}/retry", givenUp));
        assertThat(retried).hasStatusOk();
        assertThat(retried).bodyJson().extractingPath("$.status").isEqualTo("PENDING");
        await(() -> notifications.findById(givenUp).orElseThrow().getStatus() == NotificationStatus.SENT);

        assertThat(as(mvc, admin, mvc.post().uri("/api/v1/admin/notifications/{id}/retry", givenUp)))
                .bodyJson().extractingPath("$.code").isEqualTo("NOTIFICATION_ALREADY_SENT");
        assertThat(as(mvc, admin, mvc.post().uri("/api/v1/admin/notifications/{id}/retry", UUID.randomUUID())))
                .hasStatus(HttpStatus.NOT_FOUND);
        assertThat(as(mvc, customer, mvc.get().uri("/api/v1/admin/notifications"))).hasStatus(HttpStatus.FORBIDDEN);
    }

    @Test
    void nothingIsWrittenOrSentWhenTheTransactionRollsBack() {
        String rolledBack = "KRV-ROLLBACK";
        transactions.executeWithoutResult(tx -> {
            events.publishEvent(paidEvent(rolledBack));
            tx.setRollbackOnly();
        });
        assertThat(notifications.findByUserIdOrderByCreatedAtAsc(customerId)).isEmpty();

        String committed = "KRV-COMMITTED";
        transactions.executeWithoutResult(tx -> events.publishEvent(paidEvent(committed)));

        // The rows are written right after the commit; the SMS follows in the background.
        assertThat(notifications.findByUserIdOrderByCreatedAtAsc(customerId)).hasSize(1);
        await(() -> sms.messages(phone).stream().anyMatch(text -> text.contains(committed)));
        assertThat(sms.messages(phone)).noneMatch(text -> text.contains(rolledBack));
    }

    @Test
    void plainStartAnswersWithAHint() {
        long chatId = ThreadLocalRandom.current().nextLong(1_000_000, 9_000_000_000L);

        assertThat(webhook(SECRET, chatId, "/start")).hasStatusOk();
        assertThat(webhook(SECRET, chatId, "salom")).hasStatusOk();

        assertThat(telegram.messages(chatId)).containsExactly(
                "Karvon: xabarnomalarni ulash uchun kabinetdagi Telegram havolasini oching.");
    }

    private OrderStatusChangedEvent paidEvent(String number) {
        return new OrderStatusChangedEvent(UUID.randomUUID(), number, customerId, OrderStatus.AWAITING_PAYMENT,
                OrderStatus.PAID, Actor.SYSTEM, Instant.now(), OrderStatusChangedEvent.Details.NONE);
    }

    private long sentCount() {
        return notifications.findByUserIdOrderByCreatedAtAsc(customerId).stream()
                .filter(n -> n.getStatus() == NotificationStatus.SENT).count();
    }

    /** The newest notification of the customer once it has this status. */
    private UUID awaitOne(NotificationStatus status) {
        await(() -> notifications.findByUserIdOrderByCreatedAtAsc(customerId).stream()
                .anyMatch(n -> n.getStatus() == status));
        List<Notification> found = notifications.findByUserIdOrderByCreatedAtAsc(customerId).stream()
                .filter(n -> n.getStatus() == status).toList();
        return found.getLast().getId();
    }

    private MvcTestResult webhook(String secret, long chatId, String text) {
        MockMvcTester.MockMvcRequestBuilder request = mvc.post().uri(WEBHOOK).contentType(MediaType.APPLICATION_JSON)
                .content("{\"update_id\":1,\"message\":{\"message_id\":5,\"chat\":{\"id\":%d,\"type\":\"private\"},"
                        .formatted(chatId)
                        + "\"from\":{\"id\":%d,\"language_code\":\"uz\"},\"text\":\"%s\"}}".formatted(chatId, text));
        if (secret != null) {
            request = request.header("X-Telegram-Bot-Api-Secret-Token", secret);
        }
        return request.exchange();
    }

    private MvcTestResult post(String token, String uri, String id, String body) {
        return as(mvc, token, mvc.post().uri(uri, id).contentType(MediaType.APPLICATION_JSON).content(body));
    }

    /** Async sending: waits up to 10 seconds. */
    private static void await(BooleanSupplier condition) {
        long deadline = System.currentTimeMillis() + 10_000;
        while (!condition.getAsBoolean()) {
            if (System.currentTimeMillis() > deadline) {
                fail("Condition not met within 10 seconds");
            }
            try {
                Thread.sleep(50);
            } catch (InterruptedException ex) {
                Thread.currentThread().interrupt();
                fail("Interrupted");
            }
        }
    }
}
