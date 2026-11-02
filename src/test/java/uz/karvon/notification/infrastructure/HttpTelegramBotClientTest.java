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

class HttpTelegramBotClientTest {

    private static final String TOKEN = "123456:SECRET-TOKEN";
    private static final TelegramProperties PROPERTIES = new TelegramProperties("https://telegram.test", TOKEN,
            "karvon_bot", "hook-secret", Duration.ofMinutes(15), Duration.ofSeconds(5));

    private final RestClient.Builder builder = RestClient.builder();
    private final MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
    private final HttpTelegramBotClient client = new HttpTelegramBotClient(builder, PROPERTIES);

    @Test
    void postsTheTextToSendMessage() {
        server.expect(requestTo("https://telegram.test/bot" + TOKEN + "/sendMessage"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(content().json("{\"chat_id\":777,\"text\":\"Karvon: salom\"}"))
                .andRespond(withSuccess("{\"ok\":true}", MediaType.APPLICATION_JSON));

        client.sendMessage(777L, "Karvon: salom");

        server.verify();
    }

    @Test
    void refusedMessageFailsWithoutShowingTheToken() {
        server.expect(requestTo("https://telegram.test/bot" + TOKEN + "/sendMessage"))
                .andRespond(withStatus(HttpStatus.FORBIDDEN).contentType(MediaType.APPLICATION_JSON)
                        .body("{\"ok\":false,\"description\":\"Forbidden: bot was blocked by the user " + TOKEN + "\"}"));

        assertThatThrownBy(() -> client.sendMessage(777L, "Karvon: salom"))
                .isInstanceOf(TelegramSendException.class)
                .hasMessageContaining("403")
                .hasMessageContaining("bot was blocked")
                .hasMessageNotContaining(TOKEN);
    }

    @Test
    void tokenIsRequired() {
        TelegramProperties noToken = new TelegramProperties("https://telegram.test", "", "karvon_bot", "",
                Duration.ofMinutes(15), Duration.ofSeconds(5));

        assertThatThrownBy(() -> new HttpTelegramBotClient(RestClient.builder(), noToken))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("TELEGRAM_BOT_TOKEN");
    }
}
