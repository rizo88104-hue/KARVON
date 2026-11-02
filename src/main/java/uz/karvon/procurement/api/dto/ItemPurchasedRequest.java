package uz.karvon.procurement.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;

/**
 * What was paid on Taobao for one unit. More than the order price plus the threshold (3% by default) sends the
 * order to PRICE_CHANGED for the customer to decide.
 */
public record ItemPurchasedRequest(
        @Schema(example = "89.00") @NotNull @DecimalMin(value = "0", inclusive = false) @Digits(integer = 10, fraction = 2)
        BigDecimal actualPriceCny,
        @Schema(example = "3456789012345678") @Size(max = 100) String taobaoOrderNumber,
        @Schema(example = "SF1234567890") @Size(max = 100) String chinaTrackingNumber) {
}
