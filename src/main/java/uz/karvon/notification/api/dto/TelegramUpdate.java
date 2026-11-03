package uz.karvon.notification.api.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * The part of a Telegram Bot API Update the bot uses (snake_case, as Telegram sends it): text messages and a shared
 * contact. Comes from the webhook (prod) or from getUpdates (dev long polling).
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record TelegramUpdate(@JsonProperty("update_id") Long updateId, Message message) {

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Message(Chat chat, From from, String text, Contact contact) {

        public Message(Chat chat, From from, String text) {
            this(chat, from, text, null);
        }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Chat(Long id) {
    }

    /** The sender; {@code id} is the Telegram user id. */
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record From(Long id, @JsonProperty("language_code") String languageCode) {

        public From(String languageCode) {
            this(null, languageCode);
        }
    }

    /** A shared phone number; {@code userId} is set when it is a Telegram user's own contact. */
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Contact(@JsonProperty("phone_number") String phoneNumber, @JsonProperty("user_id") Long userId) {
    }
}
