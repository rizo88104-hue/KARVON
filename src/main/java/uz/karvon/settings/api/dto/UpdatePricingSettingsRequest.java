package uz.karvon.settings.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;

/**
 * The full new version of the settings. Every field is required except the delivery days: when they are left out,
 * the current values are kept. UZS amounts are whole so'm.
 */
public record UpdatePricingSettingsRequest(
        @Schema(example = "1780") @NotNull @DecimalMin(value = "0", inclusive = false) @Digits(integer = 10, fraction = 2)
        BigDecimal customerRate,
        @Schema(example = "10") @NotNull @DecimalMin("0") @DecimalMax("100") @Digits(integer = 3, fraction = 2)
        BigDecimal feePercent,
        @Schema(example = "15000") @NotNull @DecimalMin("0") @Digits(integer = 14, fraction = 0)
        BigDecimal minServiceFee,
        @Schema(example = "70000") @NotNull @DecimalMin("0") @Digits(integer = 14, fraction = 0)
        BigDecimal shippingCostPerKg,
        @Schema(example = "10000") @NotNull @DecimalMin("0") @Digits(integer = 14, fraction = 0)
        BigDecimal ourMarkupPerKg,
        @Schema(example = "0.5") @NotNull @DecimalMin("0") @Digits(integer = 5, fraction = 3)
        BigDecimal minWeightKg,
        @Schema(example = "0.5") @NotNull @DecimalMin(value = "0", inclusive = false) @Digits(integer = 5, fraction = 3)
        BigDecimal defaultWeightKg,
        @Schema(example = "3") @NotNull @DecimalMin("0") @DecimalMax("100") @Digits(integer = 3, fraction = 2)
        BigDecimal priceChangeThresholdPercent,
        @Schema(example = "12") @Min(1) @Max(365)
        Integer deliveryDaysMin,
        @Schema(example = "18") @Min(1) @Max(365)
        Integer deliveryDaysMax) {
}
