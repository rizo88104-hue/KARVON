package uz.karvon.auth.api;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.assertj.MockMvcTester;
import org.springframework.test.web.servlet.assertj.MvcTestResult;
import uz.karvon.AbstractIntegrationTest;
import uz.karvon.notification.application.TelegramBotClient.Keyboard;
import uz.karvon.notification.infrastructure.FakeTelegramBotClient;
import uz.karvon.notification.infrastructure.FakeTelegramBotClient.Sent;

import static org.assertj.core.api.Assertions.assertThat;
import static uz.karvon.TestLogins.bearer;
import static uz.karvon.TestLogins.chatId;
import static uz.karvon.TestLogins.lastCode;
import static uz.karvon.TestLogins.login;
import static uz.karvon.TestLogins.newPhone;
import static uz.karvon.TestLogins.read;
import static uz.karvon.TestLogins.requestOtp;
import static uz.karvon.TestLogins.startToken;
import static uz.karvon.TestLogins.telegramContact;
import static uz.karvon.TestLogins.telegramStart;
import static uz.karvon.TestLogins.telegramText;
import static uz.karvon.TestLogins.verifyOtp;
import static uz.karvon.payment.PaymentTestSupport.newOrder;

/**
 * Login by Telegram with SMS turned off (the test default, as in production now): a phone without a linked chat
 * gets a bot link; /start shows the contact button; only the user's own contact with the same phone links the chat
 * and brings the code. A linked phone gets the code in Telegram at once.
 */
class TelegramLoginIntegrationTest extends AbstractIntegrationTest {

    private static final String CODE_TEXT = "E-Market: tasdiqlash kodi %s. Kodni hech kimga bermang.";
    private static final String SHARE_PHONE =
            "E-Market: kirish kodini olish uchun pastdagi \"Telefon raqamni yuborish\" tugmasini bosing.";

    @Autowired
    private MockMvcTester mvc;

    @Autowired
    private FakeTelegramBotClient telegram;

    @Autowired
    private JdbcTemplate jdbc;

    @Test
    void notLinkedPhoneGetsABotLinkAndTheCodeAfterItsOwnContact() {
        String phone = newPhone();
        long chat = chatId(phone);

        MvcTestResult requested = requestOtp(mvc, phone);
        assertThat(requested).hasStatusOk();
        assertThat(requested).bodyJson().extractingPath("$.channel").isEqualTo("TELEGRAM_LINK");
        assertThat(requested).bodyJson().extractingPath("$.expiresInSeconds").isEqualTo(600);
        String link = read(requested, "$.link");
        assertThat(link).startsWith("https://t.me/karvon_test_bot?start=");
        // No code yet, and no user until the contact arrives.
        assertThat(mvc.get().uri("/api/v1/dev/otp/{phone}", phone)).hasStatus(HttpStatus.NOT_FOUND);
        assertThat(users(phone)).isZero();

        assertThat(telegramStart(mvc, chat, startToken(link))).hasStatusOk();
        assertThat(telegram.sent(chat)).last().isEqualTo(
                new Sent(SHARE_PHONE, new Keyboard.ShareContact("Telefon raqamni yuborish")));

        // Telegram sends the number without "+".
        assertThat(telegramContact(mvc, chat, chat, chat, phone.substring(1))).hasStatusOk();
        String code = lastCode(mvc, phone);
        assertThat(telegram.sent(chat)).last().isEqualTo(new Sent(CODE_TEXT.formatted(code), Keyboard.REMOVE));
        assertThat(jdbc.queryForObject("SELECT telegram_chat_id FROM users WHERE phone = ?", Long.class, phone))
                .isEqualTo(chat);

        MvcTestResult verified = verifyOtp(mvc, phone, code);
        assertThat(verified).hasStatusOk();
        assertThat(verified).bodyJson().extractingPath("$.user.roles").asArray().containsExactly("CUSTOMER");
        var settings = mvc.get().uri("/api/v1/me/notification-settings")
                .header(HttpHeaders.AUTHORIZATION, bearer(read(verified, "$.accessToken")));
        assertThat(settings).bodyJson().extractingPath("$.telegramLinked").isEqualTo(true);
        assertThat(settings).bodyJson().extractingPath("$.smsAvailable").isEqualTo(false);

        // The link works once.
        assertThat(telegramStart(mvc, chat, startToken(link))).hasStatusOk();
        assertThat(telegram.messages(chat)).last().asString().contains("havola eskirgan");
    }

    @Test
    void linkedPhoneGetsTheCodeInTelegramAtOnce() {
        String phone = newPhone();
        login(mvc, phone);

        MvcTestResult requested = requestOtp(mvc, phone);

        assertThat(requested).hasStatusOk();
        assertThat(requested).bodyJson().extractingPath("$.channel").isEqualTo("TELEGRAM");
        assertThat(requested).bodyJson().extractingPath("$.expiresInSeconds").isEqualTo(120);
        assertThat(requested).bodyJson().extractingPath("$.link").isNull();
        String code = lastCode(mvc, phone);
        assertThat(telegram.messages(chatId(phone))).last().isEqualTo(CODE_TEXT.formatted(code));
        assertThat(verifyOtp(mvc, phone, code)).hasStatusOk();
    }

    @Test
    void someoneElsesContactIsRefusedPolitely() {
        String phone = newPhone();
        long chat = chatId(phone);
        String token = startToken(read(requestOtp(mvc, phone), "$.link"));
        telegramStart(mvc, chat, token);

        // A contact from the address book: it belongs to another Telegram user.
        assertThat(telegramContact(mvc, chat, chat, 555L, phone.substring(1))).hasStatusOk();
        assertThat(telegram.sent(chat)).last().satisfies(sent -> {
            assertThat(sent.text()).isEqualTo("E-Market: iltimos, o'zingizning raqamingizni pastdagi tugma orqali yuboring.");
            assertThat(sent.keyboard()).isInstanceOf(Keyboard.ShareContact.class);
        });
        // A contact card without a Telegram user.
        assertThat(telegramContact(mvc, chat, chat, null, phone.substring(1))).hasStatusOk();
        assertThat(telegram.messages(chat)).last().asString().contains("o'zingizning raqamingizni");

        assertThat(mvc.get().uri("/api/v1/dev/otp/{phone}", phone)).hasStatus(HttpStatus.NOT_FOUND);
        assertThat(users(phone)).isZero();

        // The right contact still works afterwards.
        telegramContact(mvc, chat, chat, chat, phone.substring(1));
        assertThat(verifyOtp(mvc, phone, lastCode(mvc, phone))).hasStatusOk();
    }

    @Test
    void ownContactWithAnotherPhoneIsRefusedPolitely() {
        String phone = newPhone();
        String telegramPhone = newPhone();
        long chat = chatId(phone);
        telegramStart(mvc, chat, startToken(read(requestOtp(mvc, phone), "$.link")));

        assertThat(telegramContact(mvc, chat, chat, chat, telegramPhone.substring(1))).hasStatusOk();

        assertThat(telegram.sent(chat)).last().isEqualTo(new Sent("E-Market: kechirasiz, bu Telegram raqami saytda "
                + "kiritilgan raqamga mos kelmadi. Saytda Telegram'dagi raqamingizni kiriting va qaytadan urinib ko'ring.",
                Keyboard.REMOVE));
        assertThat(mvc.get().uri("/api/v1/dev/otp/{phone}", phone)).hasStatus(HttpStatus.NOT_FOUND);
        assertThat(mvc.get().uri("/api/v1/dev/otp/{phone}", telegramPhone)).hasStatus(HttpStatus.NOT_FOUND);
        assertThat(users(phone) + users(telegramPhone)).isZero();
    }

    @Test
    void contactOrTokenWithoutARequestIsExplained() {
        long chat = chatId(newPhone());

        telegramContact(mvc, chat, chat, chat, newPhone().substring(1));
        assertThat(telegram.messages(chat)).last().asString().contains("avval saytda telefon raqamingizni kiriting");

        telegramStart(mvc, chat, "unknown-token");
        assertThat(telegram.messages(chat)).last().asString().contains("havola eskirgan");

        telegramText(mvc, chat, "/start");
        assertThat(telegram.messages(chat)).last().asString().contains("Telegram orqali kod olish");
    }

    @Test
    void ordersCommandListsTheLastOrdersOfTheLinkedUser() {
        String phone = newPhone();
        long chat = chatId(phone);
        String customer = read(login(mvc, phone), "$.accessToken");
        String number = newOrder(mvc, customer);

        assertThat(telegramText(mvc, chat, "/buyurtmalarim")).hasStatusOk();

        assertThat(telegram.messages(chat)).last().isEqualTo(
                "E-Market: so'nggi buyurtmalaringiz:\n" + number + " - To'lov kutilmoqda, 238 200 so'm");

        long stranger = chatId(newPhone());
        telegramText(mvc, stranger, "/buyurtmalarim");
        assertThat(telegram.messages(stranger)).last().asString().contains("hech bir hisobga ulanmagan");
    }

    @Test
    void devStandInForTheBotLinksAFakeChatAndMakesACode() {
        String phone = newPhone();
        assertThat(requestOtp(mvc, phone)).bodyJson().extractingPath("$.channel").isEqualTo("TELEGRAM_LINK");

        MvcTestResult standIn = mvc.post().uri("/api/v1/dev/otp/{phone}/telegram-login", phone).exchange();

        assertThat(standIn).hasStatusOk();
        assertThat(verifyOtp(mvc, phone, read(standIn, "$.code"))).hasStatusOk();
        assertThat(requestOtp(mvc, phone)).bodyJson().extractingPath("$.channel").isEqualTo("TELEGRAM");
    }

    @Test
    void limitsStillApplyToTheLinkFlow() {
        String phone = newPhone();
        for (int i = 0; i < 3; i++) {
            assertThat(requestOtp(mvc, phone)).hasStatusOk();
        }
        MvcTestResult fourth = requestOtp(mvc, phone);
        assertThat(fourth).hasStatus(HttpStatus.TOO_MANY_REQUESTS);
        assertThat(fourth).bodyJson().extractingPath("$.code").isEqualTo("OTP_TOO_MANY_REQUESTS");
    }

    private long users(String phone) {
        Long count = jdbc.queryForObject("SELECT count(*) FROM users WHERE phone = ?", Long.class, phone);
        return count == null ? 0 : count;
    }
}
