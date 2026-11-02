package uz.karvon.order.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;

/**
 * The weighed weight of the whole parcel (all items of the order).
 */
public record WeightRequest(
        @Schema(example = "1.2") @NotNull @DecimalMin(value = "0", inclusive = false) @Digits(integer = 5, fraction = 3)
        BigDecimal realWeightKg,
        @Size(max = 500) String comment) {
}
