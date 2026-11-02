package uz.karvon.notification.api.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * The part of a Telegram Bot API Update the webhook uses (snake_case, as Telegram sends it).
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record TelegramUpdate(@JsonProperty("update_id") Long updateId, Message message) {

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Message(Chat chat, From from, String text) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Chat(Long id) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record From(@JsonProperty("language_code") String languageCode) {
    }
}
