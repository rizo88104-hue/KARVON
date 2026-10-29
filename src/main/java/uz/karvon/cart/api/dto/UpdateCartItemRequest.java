package uz.karvon.cart.api.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;

/**
 * Partial update: a null field is left unchanged; a blank comment clears it.
 */
public record UpdateCartItemRequest(
        @Min(1) @Max(99) Integer quantity,
        @Size(max = 300) String comment,
        Boolean selected) {
}
