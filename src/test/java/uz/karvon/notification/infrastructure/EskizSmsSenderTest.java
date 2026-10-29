package uz.karvon.notification.infrastructure;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;
import uz.karvon.notification.application.SmsSendException;

import java.time.Duration;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.ExpectedCount.once;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

class EskizSmsSenderTest {

    private static final String BASE = "https://eskiz.test/api";
    private static final EskizProperties PROPERTIES =
            new EskizProperties(BASE, "shop@karvon.uz", "secret", "4546", Duration.ofSeconds(5));

    private final RestClient.Builder builder = RestClient.builder();
    private final MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
    private final EskizSmsSender sender = new EskizSmsSender(builder, PROPERTIES);

    @Test
    void logsInOnceAndReusesTheToken() {
        expectLogin("token-1");
        expectSend("token-1");
        expectSend("token-1");

        sender.send("+998901234567", "Karvon: tasdiqlash kodi 12345. Kodni hech kimga bermang.");
        sender.send("+998901234567", "Karvon: tasdiqlash kodi 12345. Kodni hech kimga bermang.");

        server.verify();
    }

    @Test
    void expiredTokenIsRefreshedAndTheSmsIsSentAgain() {
        expectLogin("old");
        server.expect(once(), requestTo(BASE + "/message/sms/send"))
                .andRespond(withStatus(HttpStatus.UNAUTHORIZED));
        expectLogin("new");
        expectSend("new");

        sender.send("+998901234567", "Karvon: tasdiqlash kodi 12345. Kodni hech kimga bermang.");

        server.verify();
    }

    @Test
    void providerErrorBecomesSmsSendException() {
        expectLogin("token-1");
        server.expect(once(), requestTo(BASE + "/message/sms/send"))
                .andRespond(withStatus(HttpStatus.INTERNAL_SERVER_ERROR));

        assertThatThrownBy(() -> sender.send("+998901234567", "text"))
                .isInstanceOf(SmsSendException.class)
                .hasFieldOrPropertyWithValue("code", "SMS_SEND_FAILED");
    }

    @Test
    void wrongCredentialsBecomeSmsSendException() {
        server.expect(once(), requestTo(BASE + "/auth/login")).andRespond(withStatus(HttpStatus.UNAUTHORIZED));

        assertThatThrownBy(() -> sender.send("+998901234567", "text")).isInstanceOf(SmsSendException.class);
    }

    private void expectLogin(String token) {
        server.expect(once(), requestTo(BASE + "/auth/login"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(content().formDataContains(Map.of("email", "shop@karvon.uz", "password", "secret")))
                .andRespond(withSuccess("""
                        {"message":"token_generated","data":{"token":"%s"},"token_type":"bearer"}""".formatted(token),
                        MediaType.APPLICATION_JSON));
    }

    private void expectSend(String token) {
        server.expect(once(), requestTo(BASE + "/message/sms/send"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(content().formDataContains(Map.of(
                        "mobile_phone", "998901234567",
                        "message", "Karvon: tasdiqlash kodi 12345. Kodni hech kimga bermang.",
                        "from", "4546")))
                .andRespond(withSuccess("{\"id\":\"1\",\"status\":\"waiting\"}", MediaType.APPLICATION_JSON));
    }
}
