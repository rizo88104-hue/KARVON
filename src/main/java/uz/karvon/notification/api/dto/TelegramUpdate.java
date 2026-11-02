package uz.karvon.notification.api.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * The part of a Telegram Bot API Update we read: a text message and its chat.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record TelegramUpdate(@JsonProperty("update_id") Long updateId, Message message) {

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Message(Chat chat, String text) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Chat(Long id) {
    }
}
