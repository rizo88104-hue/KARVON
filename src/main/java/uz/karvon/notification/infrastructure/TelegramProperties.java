package uz.karvon.notification.infrastructure;

import jakarta.validation.constraints.NotBlank;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;
import org.springframework.validation.annotation.Validated;

import java.time.Duration;

/**
 * Our Telegram bot, used in every profile: its username (for t.me deep links), the secret Telegram sends in the
 * X-Telegram-Bot-Api-Secret-Token header of webhook calls (set with setWebhook secret_token) and how long a
 * link from GET /api/v1/me/telegram-link works.
 */
@Validated
@ConfigurationProperties("karvon.telegram")
public record TelegramProperties(
        @NotBlank String botUsername,
        @NotBlank String webhookSecret,
        @DefaultValue("15m") Duration linkTtl) {
}
