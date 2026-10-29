package uz.karvon.order.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import uz.karvon.order.domain.DeliveryMethod;
import uz.karvon.order.domain.PaymentMethod;

import java.util.UUID;

/**
 * Orders the selected cart lines. {@code addressId} (one of the customer addresses) is required for POST and COURIER.
 */
public record CreateOrderRequest(
        @Schema(example = "POST") @NotNull DeliveryMethod deliveryMethod,
        UUID addressId,
        @Schema(example = "PAYME") @NotNull PaymentMethod paymentMethod,
        @Size(max = 500) String comment) {
}
