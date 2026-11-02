package uz.karvon.procurement.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Size;

/**
 * The comment is shown to the customer with the item.
 */
public record ItemOutOfStockRequest(@Schema(example = "Sotuvchida bu o'lcham tugagan") @Size(max = 500) String comment) {
}
