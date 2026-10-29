package uz.karvon;

import com.jayway.jsonpath.JsonPath;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.assertj.MockMvcTester;
import org.springframework.test.web.servlet.assertj.MvcTestResult;

import java.io.UnsupportedEncodingException;
import java.util.concurrent.ThreadLocalRandom;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Logs in through the real OTP flow (request code, read it from the dev endpoint, verify) for integration tests.
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

    /** The code the fake SMS sender got for this phone, read through GET /api/v1/dev/otp/{phone}. */
    public static String lastCode(MockMvcTester mvc, String phone) {
        MvcTestResult result = mvc.get().uri("/api/v1/dev/otp/{phone}", phone).exchange();
        assertThat(result).hasStatusOk();
        return read(result, "$.code");
    }

    /** Full login; returns the verify response. */
    public static MvcTestResult login(MockMvcTester mvc, String phone) {
        assertThat(requestOtp(mvc, phone)).hasStatusOk();
        MvcTestResult result = verifyOtp(mvc, phone, lastCode(mvc, phone));
        assertThat(result).hasStatusOk();
        return result;
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
