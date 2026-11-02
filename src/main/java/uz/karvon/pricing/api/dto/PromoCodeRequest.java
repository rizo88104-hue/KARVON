package uz.karvon.pricing.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import uz.karvon.pricing.domain.PromoCodeType;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * A promo code. {@code value}: percent of the service fee for PERCENT_OF_FEE (at most 100), so'm for FIXED_UZS.
 * {@code maxUses}/{@code perUserLimit}/{@code validFrom}/{@code validTo} are optional; {@code validTo} is exclusive.
 * The code is stored in upper case.
 */
public record PromoCodeRequest(
        @Schema(example = "KARVON10") @NotBlank @Size(max = 40) @Pattern(regexp = "[A-Za-z0-9_-]+") String code,
        @Schema(example = "PERCENT_OF_FEE") @NotNull PromoCodeType type,
        @Schema(example = "50") @NotNull @DecimalMin(value = "0", inclusive = false) @Digits(integer = 12, fraction = 2)
        BigDecimal value,
        @Schema(example = "1000") @Min(1) Integer maxUses,
        @Schema(example = "1") @Min(1) Integer perUserLimit,
        Instant validFrom,
        Instant validTo,
        boolean active) {
}
