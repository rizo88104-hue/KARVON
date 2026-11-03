package uz.karvon.notification.infrastructure;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

import java.time.Duration;

/**
 * Telegram bot. {@code botToken} is needed by the real client (prod, and dev when TELEGRAM_BOT_TOKEN is set);
 * an empty {@code webhookSecret} refuses every webhook call.
 *
 * @param botUsername the bot name without "@", used in the deep link https://t.me/{botUsername}?start=...
 * @param linkTtl     how long a cabinet deep link (linking Telegram to a signed-in user) can be used
 * @param loginTtl    how long a login deep link (POST /auth/otp/request without a linked chat) can be used
 */
@ConfigurationProperties("karvon.telegram")
public record TelegramProperties(
        @DefaultValue("https://api.telegram.org") String apiUrl,
        @DefaultValue("") String botToken,
        @DefaultValue("") String botUsername,
        @DefaultValue("") String webhookSecret,
        @DefaultValue("15m") Duration linkTtl,
        @DefaultValue("10s") Duration timeout,
        @DefaultValue("10m") Duration loginTtl) {
}
