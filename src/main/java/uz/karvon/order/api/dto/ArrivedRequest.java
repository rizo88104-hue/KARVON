package uz.karvon.order.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Size;

/**
 * {@code pickupInfo} is shown to the customer: where and how to collect the parcel.
 */
public record ArrivedRequest(
        @Schema(example = "Toshkent, Chilonzor 9, kargo ofisi. Pasport bilan keling, 10:00-19:00")
        @Size(max = 500) String pickupInfo,
        @Size(max = 500) String comment) {
}
