package uz.karvon;

import com.jayway.jsonpath.JsonPath;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.assertj.MockMvcTester;
import org.springframework.test.web.servlet.assertj.MvcTestResult;

import java.io.UnsupportedEncodingException;
import java.util.concurrent.ThreadLocalRandom;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Logs in through the real OTP flow for integration tests: request a code, take the Telegram steps when the answer
 * is a bot link, read the code from the dev endpoint, verify.
 */
public final class TestLogins {

    /** Created on startup from ADMIN_PHONE in application-test.yml. */
    public static final String ADMIN_PHONE = "+998900000001";

    private static String adminToken;

    private TestLogins() {
    }

    /**
     * One admin login for the whole test run: the Spring context and Redis are shared,
     * and a login per test class would hit the limit of 3 codes per 10 minutes.
     */
    public static synchronized String adminToken(MockMvcTester mvc) {
        if (adminToken == null) {
            adminToken = accessToken(mvc, ADMIN_PHONE);
        }
        return adminToken;
    }

    /** A random Uzbek number, so tests sharing the database and Redis do not see each other's limits. */
    public static String newPhone() {
        return "+99899" + "%07d".formatted(ThreadLocalRandom.current().nextInt(10_000_000));
    }

    public static MvcTestResult requestOtp(MockMvcTester mvc, String phone) {
        return mvc.post().uri("/api/v1/auth/otp/request")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"phone\":\"%s\"}".formatted(phone))
                .exchange();
    }

    public static MvcTestResult verifyOtp(MockMvcTester mvc, String phone, String code) {
        return mvc.post().uri("/api/v1/auth/otp/verify")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"phone\":\"%s\",\"code\":\"%s\"}".formatted(phone, code))
                .exchange();
    }

    public static MvcTestResult refresh(MockMvcTester mvc, String refreshToken) {
        return mvc.post().uri("/api/v1/auth/refresh")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"refreshToken\":\"%s\"}".formatted(refreshToken))
                .exchange();
    }

    /** The last code made for this phone (any channel), read through GET /api/v1/dev/otp/{phone}. */
    public static String lastCode(MockMvcTester mvc, String phone) {
        MvcTestResult result = mvc.get().uri("/api/v1/dev/otp/{phone}", phone).exchange();
        assertThat(result).hasStatusOk();
        return read(result, "$.code");
    }

    /**
     * Requests a code exactly as a customer gets it: a linked chat receives it at once; without one the answer is a
     * bot link (TELEGRAM_LINK), so the test plays the customer in Telegram (/start with the token, then their own
     * contact) through the webhook. Returns the code.
     */
    public static String requestCode(MockMvcTester mvc, String phone) {
        MvcTestResult requested = requestOtp(mvc, phone);
        assertThat(requested).hasStatusOk();
        if ("TELEGRAM_LINK".equals(read(requested, "$.channel"))) {
            long chat = chatId(phone);
            assertThat(telegramStart(mvc, chat, startToken(read(requested, "$.link")))).hasStatusOk();
            assertThat(telegramContact(mvc, chat, chat, chat, phone.substring(1))).hasStatusOk();
        }
        return lastCode(mvc, phone);
    }

    /** Full login; returns the verify response. */
    public static MvcTestResult login(MockMvcTester mvc, String phone) {
        MvcTestResult result = verifyOtp(mvc, phone, requestCode(mvc, phone));
        assertThat(result).hasStatusOk();
        return result;
    }

    // --- the customer in Telegram (webhook calls as Telegram makes them)

    public static final String TELEGRAM_SECRET = "test-telegram-secret";

    /** The Telegram chat (and user id) tests use for a phone: its last 9 digits. */
    public static long chatId(String phone) {
        return Long.parseLong(phone.substring(phone.length() - 9));
    }

    /** The token of https://t.me/bot?start={token}. */
    public static String startToken(String link) {
        return link.substring(link.indexOf("?start=") + "?start=".length());
    }

    public static MvcTestResult telegramText(MockMvcTester mvc, long chatId, String text) {
        return telegramUpdate(mvc, """
                {"update_id":1,"message":{"chat":{"id":%d},"from":{"id":%d,"language_code":"uz"},"text":"%s"}}"""
                .formatted(chatId, chatId, text));
    }

    public static MvcTestResult telegramStart(MockMvcTester mvc, long chatId, String token) {
        return telegramText(mvc, chatId, "/start " + token);
    }

    /** A shared contact: {@code fromId} sent it, {@code contactUserId} is whose contact it is. */
    public static MvcTestResult telegramContact(MockMvcTester mvc, long chatId, long fromId, Long contactUserId,
                                                String phoneNumber) {
        return telegramUpdate(mvc, """
                {"update_id":2,"message":{"chat":{"id":%d},"from":{"id":%d},
                 "contact":{"phone_number":"%s","user_id":%s}}}"""
                .formatted(chatId, fromId, phoneNumber, contactUserId));
    }

    public static MvcTestResult telegramUpdate(MockMvcTester mvc, String json) {
        return mvc.post().uri("/api/v1/telegram/webhook")
                .header("X-Telegram-Bot-Api-Secret-Token", TELEGRAM_SECRET)
                .contentType(MediaType.APPLICATION_JSON)
                .content(json)
                .exchange();
    }

    /** A new user who has only this staff role (given by the admin); returns the access token. */
    public static String staffToken(MockMvcTester mvc, String role) {
        MvcTestResult login = login(mvc, newPhone());
        MvcTestResult changed = mvc.patch().uri("/api/v1/admin/users/{id}/roles", read(login, "$.user.id"))
                .header(HttpHeaders.AUTHORIZATION, bearer(adminToken(mvc)))
                .contentType(MediaType.APPLICATION_JSON).content("{\"roles\":[\"%s\"]}".formatted(role)).exchange();
        assertThat(changed).hasStatusOk();
        return read(login, "$.accessToken");
    }

    public static String accessToken(MockMvcTester mvc, String phone) {
        return read(login(mvc, phone), "$.accessToken");
    }

    public static String bearer(String accessToken) {
        return "Bearer " + accessToken;
    }

    public static String read(MvcTestResult result, String path) {
        try {
            return JsonPath.read(result.getResponse().getContentAsString(), path).toString();
        } catch (UnsupportedEncodingException ex) {
            throw new IllegalStateException(ex);
        }
    }

    /** A code different from {@code code}. */
    public static String wrongCode(String code) {
        return code.equals("00000") ? "11111" : "00000";
    }
}
