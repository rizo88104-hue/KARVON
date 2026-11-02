package uz.karvon.notification.infrastructure;

import org.springframework.http.MediaType;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;
import uz.karvon.notification.application.TelegramSendException;
import uz.karvon.notification.application.TelegramSender;

import java.util.Map;

/**
 * Telegram Bot API over HTTP: POST {baseUrl}/bot{token}/sendMessage.
 * The token is part of the URL, so errors are rewritten without the URL before they reach logs or the database.
 */
public class TelegramBotClient implements TelegramSender {

    private static final int MAX_TEXT = 4096;

    private final RestClient http;
    private final String token;
    private final String sendMessagePath;

    public TelegramBotClient(RestClient.Builder builder, TelegramBotProperties properties) {
        this.http = builder.baseUrl(properties.baseUrl()).build();
        this.token = properties.token();
        this.sendMessagePath = "/bot" + token + "/sendMessage";
    }

    @Override
    public void send(long chatId, String text) {
        String body = text.length() <= MAX_TEXT ? text : text.substring(0, MAX_TEXT);
        try {
            // Literal path, not a URI variable: a variable would encode the ':' of the token.
            http.post().uri(sendMessagePath)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(Map.of("chat_id", chatId, "text", body))
                    .retrieve()
                    .toBodilessEntity();
        } catch (RestClientResponseException ex) {
            // Telegram explains the error in the body, e.g. {"ok":false,"description":"Forbidden: bot was blocked by the user"}
            throw new TelegramSendException("Telegram answered " + ex.getStatusCode().value() + ": "
                    + ex.getResponseBodyAsString().replace(token, "***"));
        } catch (RestClientException ex) {
            throw new TelegramSendException("Telegram unreachable: " + rootCause(ex).getClass().getSimpleName());
        }
    }

    private static Throwable rootCause(Throwable ex) {
        Throwable cause = ex;
        while (cause.getCause() != null && cause.getCause() != cause) {
            cause = cause.getCause();
        }
        return cause;
    }
}
