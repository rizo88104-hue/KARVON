package uz.karvon.notification.infrastructure;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import uz.karvon.notification.application.SmsSendException;
import uz.karvon.notification.application.SmsSender;

/**
 * Sends SMS through Eskiz.uz. The API token is fetched with email/password on first use and cached;
 * when Eskiz answers 401 (the token lives about 30 days) it logs in again and retries once.
 */
public class EskizSmsSender implements SmsSender {

    private static final Logger log = LoggerFactory.getLogger(EskizSmsSender.class);

    private final RestClient http;
    private final EskizProperties properties;
    private volatile String token;

    public EskizSmsSender(RestClient.Builder builder, EskizProperties properties) {
        this.http = builder.baseUrl(properties.baseUrl()).build();
        this.properties = properties;
    }

    @Override
    public void send(String phone, String text) {
        try {
            try {
                sendWith(currentToken(), phone, text);
            } catch (HttpClientErrorException.Unauthorized expired) {
                log.info("Eskiz token expired, logging in again");
                token = null;
                sendWith(currentToken(), phone, text);
            }
        } catch (RestClientException | EskizLoginException ex) {
            log.error("Eskiz did not send SMS to {}", phone, ex);
            throw new SmsSendException(ex);
        }
    }

    private void sendWith(String bearer, String phone, String text) {
        MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
        // Eskiz expects the number without the plus sign: 998XXXXXXXXX
        form.add("mobile_phone", phone.startsWith("+") ? phone.substring(1) : phone);
        form.add("message", text);
        form.add("from", properties.from());
        http.post().uri("/message/sms/send")
                .headers(headers -> headers.setBearerAuth(bearer))
                .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                .body(form)
                .retrieve()
                .toBodilessEntity();
    }

    private synchronized String currentToken() {
        if (token == null) {
            token = login();
        }
        return token;
    }

    private String login() {
        MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
        form.add("email", properties.email());
        form.add("password", properties.password());
        LoginResponse response = http.post().uri("/auth/login")
                .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                .body(form)
                .retrieve()
                .onStatus(status -> status.isSameCodeAs(HttpStatus.UNAUTHORIZED), (request, reply) -> {
                    throw new EskizLoginException("Eskiz rejected ESKIZ_EMAIL / ESKIZ_PASSWORD");
                })
                .body(LoginResponse.class);
        if (response == null || response.data() == null || response.data().token() == null) {
            throw new EskizLoginException("Eskiz login returned no token");
        }
        return response.data().token();
    }

    static class EskizLoginException extends RuntimeException {
        EskizLoginException(String message) {
            super(message);
        }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record LoginResponse(TokenData data) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record TokenData(String token) {
    }
}
