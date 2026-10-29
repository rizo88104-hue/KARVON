package uz.karvon.cart.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * {@code quantity} defaults to 1. Adding a SKU that is already in the cart adds to its quantity.
 */
public record AddCartItemRequest(
        @Schema(example = "680000001117") @NotBlank @Size(max = 20) String externalProductId,
        @Schema(example = "680000001117001") @NotBlank @Size(max = 40) String skuId,
        @Schema(example = "1") @Min(1) @Max(99) Integer quantity,
        @Schema(example = "Iltimos, qutisi bilan") @Size(max = 300) String comment) {
}
