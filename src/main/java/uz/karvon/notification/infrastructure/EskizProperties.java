package uz.karvon.notification.infrastructure;

import jakarta.validation.constraints.NotBlank;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;
import org.springframework.validation.annotation.Validated;

import java.time.Duration;

/**
 * Eskiz.uz account. Bound only outside dev/test, where the real sender is used.
 */
@Validated
@ConfigurationProperties("karvon.sms.eskiz")
public record EskizProperties(
        @DefaultValue("https://notify.eskiz.uz/api") @NotBlank String baseUrl,
        @NotBlank String email,
        @NotBlank String password,
        @NotBlank String from,
        @DefaultValue("10s") Duration timeout) {
}
