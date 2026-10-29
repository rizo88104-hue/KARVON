package uz.karvon.auth.infrastructure;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import java.time.Duration;

/**
 * Login settings ({@code karvon.auth.*}). {@code jwtSecret} comes from JWT_SECRET.
 */
@Validated
@ConfigurationProperties("karvon.auth")
public record AuthProperties(
        @NotBlank String jwtSecret,
        @NotNull Duration accessTokenTtl,
        @NotNull Duration refreshTokenTtl,
        @Valid @NotNull Otp otp) {

    /**
     * @param codeTtl          how long a sent code is valid
     * @param maxRequests      codes that may be requested for one phone within {@code requestWindow}
     * @param maxWrongAttempts wrong codes after which the phone is blocked for {@code blockDuration}
     */
    public record Otp(
            @NotNull Duration codeTtl,
            @Min(1) int maxRequests,
            @NotNull Duration requestWindow,
            @Min(1) int maxWrongAttempts,
            @NotNull Duration blockDuration) {
    }
}
