package uz.karvon.order.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import uz.karvon.order.application.OrderBulkService;
import uz.karvon.order.domain.OrderStatus;

import java.util.List;
import java.util.UUID;

/**
 * Orders that move together: IN_TRANSIT (with a shared {@code trackingNumber}), ARRIVED or DELIVERED.
 */
public record BulkStatusRequest(
        @NotEmpty @Size(max = OrderBulkService.MAX_ORDERS) List<@NotNull UUID> orderIds,
        @Schema(example = "IN_TRANSIT") @NotNull OrderStatus toStatus,
        @Size(max = 100) String trackingNumber) {
}
