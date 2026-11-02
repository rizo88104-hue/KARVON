package uz.karvon.notification.infrastructure;

import jakarta.validation.constraints.NotBlank;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;
import org.springframework.validation.annotation.Validated;

import java.time.Duration;

/**
 * Bot API access (TELEGRAM_BOT_TOKEN). Bound only outside dev/test, where the real client is used.
 */
@Validated
@ConfigurationProperties("karvon.telegram.bot")
public record TelegramBotProperties(
        @DefaultValue("https://api.telegram.org") @NotBlank String baseUrl,
        @NotBlank String token,
        @DefaultValue("10s") Duration timeout) {
}
