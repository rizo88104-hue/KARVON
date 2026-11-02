package uz.karvon.notification.infrastructure;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;
import uz.karvon.notification.application.TelegramSendException;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

class TelegramBotClientTest {

    private static final String BASE = "https://telegram.test";
    private static final String TOKEN = "123456:secret-bot-token";

    private final RestClient.Builder builder = RestClient.builder();
    private final MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
    private final TelegramBotClient client =
            new TelegramBotClient(builder, new TelegramBotProperties(BASE, TOKEN, Duration.ofSeconds(5)));

    @Test
    void sendsTheMessageToTheChat() {
        server.expect(requestTo(BASE + "/bot" + TOKEN + "/sendMessage"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(content().json("{\"chat_id\":42,\"text\":\"Karvon: K-1 yo'lga chiqdi.\"}"))
                .andRespond(withSuccess("{\"ok\":true}", MediaType.APPLICATION_JSON));

        client.send(42, "Karvon: K-1 yo'lga chiqdi.");

        server.verify();
    }

    @Test
    void errorsKeepTelegramsReasonButNeverTheToken() {
        server.expect(requestTo(BASE + "/bot" + TOKEN + "/sendMessage"))
                .andRespond(withStatus(HttpStatus.FORBIDDEN).contentType(MediaType.APPLICATION_JSON)
                        .body("{\"ok\":false,\"description\":\"Forbidden: bot was blocked by the user\"}"));

        assertThatThrownBy(() -> client.send(42, "x"))
                .isInstanceOf(TelegramSendException.class)
                .hasMessageContaining("403")
                .hasMessageContaining("bot was blocked by the user")
                .hasMessageNotContaining(TOKEN)
                .hasNoCause();
    }
}
