package uz.karvon.notification.infrastructure;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

import java.time.Duration;

/**
 * Telegram bot. {@code botToken} is needed only by the real client (not in dev/test);
 * an empty {@code webhookSecret} refuses every webhook call.
 *
 * @param botUsername the bot name without "@", used in the deep link https://t.me/{botUsername}?start=...
 * @param linkTtl     how long a deep link can be used
 */
@ConfigurationProperties("karvon.telegram")
public record TelegramProperties(
        @DefaultValue("https://api.telegram.org") String apiUrl,
        @DefaultValue("") String botToken,
        @DefaultValue("") String botUsername,
        @DefaultValue("") String webhookSecret,
        @DefaultValue("15m") Duration linkTtl,
        @DefaultValue("10s") Duration timeout) {
}
