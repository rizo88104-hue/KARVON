package uz.karvon.pricing.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;

/**
 * Homepage calculator. {@code chinaShippingCny} defaults to 0, {@code quantity} to 1,
 * a missing {@code weightKg} means the default weight from the settings.
 */
public record EstimateRequest(
        @Schema(example = "129") @NotNull @DecimalMin(value = "0", inclusive = false) @Digits(integer = 8, fraction = 2)
        BigDecimal priceCny,
        @Schema(example = "0") @DecimalMin("0") @Digits(integer = 8, fraction = 2)
        BigDecimal chinaShippingCny,
        @Schema(example = "1") @Min(1) @Max(999)
        Integer quantity,
        @Schema(example = "0.9") @DecimalMin(value = "0", inclusive = false) @Digits(integer = 5, fraction = 3)
        BigDecimal weightKg) {
}
