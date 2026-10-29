package uz.karvon.auth.api;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.assertj.MockMvcTester;
import org.springframework.test.web.servlet.assertj.MvcTestResult;
import uz.karvon.AbstractIntegrationTest;
import uz.karvon.notification.infrastructure.FakeSmsSender;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static uz.karvon.TestLogins.bearer;
import static uz.karvon.TestLogins.lastCode;
import static uz.karvon.TestLogins.login;
import static uz.karvon.TestLogins.newPhone;
import static uz.karvon.TestLogins.read;
import static uz.karvon.TestLogins.refresh;
import static uz.karvon.TestLogins.requestOtp;
import static uz.karvon.TestLogins.verifyOtp;
import static uz.karvon.TestLogins.wrongCode;

class AuthIntegrationTest extends AbstractIntegrationTest {

    @Autowired
    private MockMvcTester mvc;

    @Autowired
    private FakeSmsSender sms;

    @Test
    void otpHappyPathCreatesCustomerAndReturnsTokens() {
        String phone = newPhone();

        MvcTestResult requested = requestOtp(mvc, phone);
        assertThat(requested).hasStatusOk();
        assertThat(requested).bodyJson().extractingPath("$.expiresInSeconds").isEqualTo(120);

        String code = lastCode(mvc, phone);
        assertThat(sms.lastMessage(phone))
                .hasValue("Karvon: tasdiqlash kodi " + code + ". Kodni hech kimga bermang.");

        MvcTestResult verified = verifyOtp(mvc, phone, code);
        assertThat(verified).hasStatusOk();
        assertThat(verified).bodyJson().extractingPath("$.user.phone").isEqualTo(phone);
        assertThat(verified).bodyJson().extractingPath("$.user.roles").asArray().containsExactly("CUSTOMER");
        assertThat(verified).bodyJson().extractingPath("$.user.language").isEqualTo("UZ");
        assertThat(verified).bodyJson().extractingPath("$.refreshToken").asString().isNotBlank();

        var me = mvc.get().uri("/api/v1/me")
                .header(HttpHeaders.AUTHORIZATION, bearer(read(verified, "$.accessToken")));
        assertThat(me).hasStatusOk();
        assertThat(me).bodyJson().extractingPath("$.id").isEqualTo(read(verified, "$.user.id"));
    }

    @Test
    void secondLoginReturnsTheSameUser() {
        String phone = newPhone();

        String firstId = read(login(mvc, phone), "$.user.id");
        String secondId = read(login(mvc, phone), "$.user.id");

        assertThat(secondId).isEqualTo(firstId);
    }

    @Test
    void phoneIsNormalized() {
        String phone = newPhone();
        String written = phone.substring(1, 6) + " " + phone.substring(6, 9) + "-" + phone.substring(9);

        assertThat(requestOtp(mvc, written)).hasStatusOk();

        assertThat(verifyOtp(mvc, phone, lastCode(mvc, phone))).hasStatusOk();
    }

    @Test
    void nonUzbekPhoneIsRejected() {
        MvcTestResult result = requestOtp(mvc, "+79001234567");

        assertThat(result).hasStatus(HttpStatus.BAD_REQUEST);
        assertThat(result).bodyJson().extractingPath("$.code").isEqualTo("PHONE_INVALID");
    }

    @Test
    void wrongCodeIsRejectedAndTellsAttemptsLeft() {
        String phone = newPhone();
        requestOtp(mvc, phone);
        String code = lastCode(mvc, phone);

        MvcTestResult result = verifyOtp(mvc, phone, wrongCode(code));

        assertThat(result).hasStatus(HttpStatus.BAD_REQUEST);
        assertThat(result).bodyJson().extractingPath("$.code").isEqualTo("OTP_INVALID");
        assertThat(result).bodyJson().extractingPath("$.detail").isEqualTo("Kod noto'g'ri. Yana 4 ta urinish qoldi");
        // The right code still works after a mistake.
        assertThat(verifyOtp(mvc, phone, code)).hasStatusOk();
    }

    @Test
    void codeCanBeUsedOnlyOnce() {
        String phone = newPhone();
        requestOtp(mvc, phone);
        String code = lastCode(mvc, phone);
        assertThat(verifyOtp(mvc, phone, code)).hasStatusOk();

        MvcTestResult again = verifyOtp(mvc, phone, code);

        assertThat(again).hasStatus(HttpStatus.BAD_REQUEST);
        assertThat(again).bodyJson().extractingPath("$.code").isEqualTo("OTP_EXPIRED");
    }

    @Test
    void fourthRequestWithinTenMinutesIsRateLimited() {
        String phone = newPhone();
        for (int i = 0; i < 3; i++) {
            assertThat(requestOtp(mvc, phone)).hasStatusOk();
        }

        MvcTestResult fourth = requestOtp(mvc, phone);

        assertThat(fourth).hasStatus(HttpStatus.TOO_MANY_REQUESTS);
        assertThat(fourth).bodyJson().extractingPath("$.code").isEqualTo("OTP_TOO_MANY_REQUESTS");
    }

    @Test
    void fiveWrongCodesBlockThePhone() {
        String phone = newPhone();
        requestOtp(mvc, phone);
        String code = lastCode(mvc, phone);

        for (int i = 0; i < 4; i++) {
            assertThat(verifyOtp(mvc, phone, wrongCode(code))).hasStatus(HttpStatus.BAD_REQUEST);
        }
        MvcTestResult fifth = verifyOtp(mvc, phone, wrongCode(code));

        assertThat(fifth).hasStatus(HttpStatus.TOO_MANY_REQUESTS);
        assertThat(fifth).bodyJson().extractingPath("$.code").isEqualTo("OTP_BLOCKED");
        assertThat(fifth).bodyJson().extractingPath("$.detail")
                .isEqualTo("Juda ko'p noto'g'ri urinish. 15 daqiqadan keyin qayta urinib ko'ring");
        // While blocked, even the right code and new requests are refused.
        assertThat(verifyOtp(mvc, phone, code)).hasStatus(HttpStatus.TOO_MANY_REQUESTS);
        assertThat(requestOtp(mvc, phone)).hasStatus(HttpStatus.TOO_MANY_REQUESTS);
    }

    @Test
    void refreshRotatesTheTokenPair() {
        MvcTestResult login = login(mvc, newPhone());
        String oldRefresh = read(login, "$.refreshToken");

        MvcTestResult refreshed = refresh(mvc, oldRefresh);

        assertThat(refreshed).hasStatusOk();
        String newRefresh = read(refreshed, "$.refreshToken");
        assertThat(newRefresh).isNotEqualTo(oldRefresh);
        assertThat(read(refreshed, "$.user.id")).isEqualTo(read(login, "$.user.id"));
        assertThat(mvc.get().uri("/api/v1/me")
                .header(HttpHeaders.AUTHORIZATION, bearer(read(refreshed, "$.accessToken")))).hasStatusOk();

        // The old token was revoked by the rotation.
        MvcTestResult reused = refresh(mvc, oldRefresh);
        assertThat(reused).hasStatus(HttpStatus.UNAUTHORIZED);
        assertThat(reused).bodyJson().extractingPath("$.code").isEqualTo("REFRESH_TOKEN_INVALID");
        // Reuse of a revoked token ends every session of the user, including the new one.
        assertThat(refresh(mvc, newRefresh)).hasStatus(HttpStatus.UNAUTHORIZED);
    }

    @Test
    void unknownRefreshTokenIsRejected() {
        assertThat(refresh(mvc, UUID.randomUUID().toString())).hasStatus(HttpStatus.UNAUTHORIZED);
    }

    @Test
    void logoutRevokesTheRefreshToken() {
        String refreshToken = read(login(mvc, newPhone()), "$.refreshToken");

        var logout = mvc.post().uri("/api/v1/auth/logout")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"refreshToken\":\"%s\"}".formatted(refreshToken));

        assertThat(logout).hasStatus(HttpStatus.NO_CONTENT);
        assertThat(refresh(mvc, refreshToken)).hasStatus(HttpStatus.UNAUTHORIZED);
    }

    @Test
    void devOtpEndpointAnswers404WhenNoCodeWasSent() {
        var result = mvc.get().uri("/api/v1/dev/otp/{phone}", newPhone());

        assertThat(result).hasStatus(HttpStatus.NOT_FOUND);
        assertThat(result).bodyJson().extractingPath("$.code").isEqualTo("DEV_OTP_NOT_FOUND");
    }

    @Test
    void protectedEndpointsRejectMissingAndInvalidTokens() {
        assertThat(mvc.get().uri("/api/v1/me")).hasStatus(HttpStatus.UNAUTHORIZED);

        var invalid = mvc.get().uri("/api/v1/me").header(HttpHeaders.AUTHORIZATION, bearer("not-a-jwt"));
        assertThat(invalid).hasStatus(HttpStatus.UNAUTHORIZED);
        assertThat(invalid).bodyJson().extractingPath("$.code").isEqualTo("UNAUTHORIZED");
    }
}
