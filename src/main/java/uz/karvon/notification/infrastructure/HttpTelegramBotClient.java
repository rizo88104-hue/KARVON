package uz.karvon.notification.infrastructure;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;
import uz.karvon.notification.application.TelegramBotClient;
import uz.karvon.notification.application.TelegramSendException;

import java.util.Map;

/**
 * Telegram Bot API over HTTP: POST {apiUrl}/bot{token}/sendMessage. The token is part of the URL, so it is removed
 * from every error text before it is logged or stored.
 */
public class HttpTelegramBotClient implements TelegramBotClient {

    private static final Logger log = LoggerFactory.getLogger(HttpTelegramBotClient.class);

    private final RestClient http;
    private final String token;

    public HttpTelegramBotClient(RestClient.Builder builder, TelegramProperties properties) {
        if (properties.botToken() == null || properties.botToken().isBlank()) {
            throw new IllegalStateException("TELEGRAM_BOT_TOKEN is not set");
        }
        this.token = properties.botToken();
        // In the base URL, not a URI variable: a variable would encode the ":" of the token as %3A.
        this.http = builder.baseUrl(properties.apiUrl() + "/bot" + token).build();
    }

    @Override
    public void sendMessage(long chatId, String text) {
        try {
            http.post().uri("/sendMessage")
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(Map.of("chat_id", chatId, "text", text))
                    .retrieve()
                    .toBodilessEntity();
        } catch (RestClientResponseException ex) {
            String error = "Telegram answered " + ex.getStatusCode().value() + ": " + hideToken(ex.getResponseBodyAsString());
            log.warn("Telegram did not send to chat {}: {}", chatId, error);
            throw new TelegramSendException(error);
        } catch (RestClientException ex) {
            String error = "Telegram is not reachable: " + hideToken(ex.getMessage());
            log.warn("Telegram did not send to chat {}: {}", chatId, error);
            throw new TelegramSendException(error);
        }
    }

    private String hideToken(String text) {
        return text == null ? "" : text.replace(token, "***");
    }
}
