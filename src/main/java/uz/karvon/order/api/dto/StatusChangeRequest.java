package uz.karvon.order.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import uz.karvon.order.domain.OrderStatus;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

/**
 * A staff status change. Extra data by target status:
 * AT_WAREHOUSE needs {@code realWeightKg} (all items together); IN_TRANSIT may set {@code internationalTrackingNumber};
 * OUT_OF_STOCK and PRICE_CHANGED need {@code items} (for PRICE_CHANGED each with {@code actualPriceCny}).
 */
public record StatusChangeRequest(
        @Schema(example = "PURCHASING") @NotNull OrderStatus toStatus,
        @Size(max = 500) String comment,
        @Schema(example = "1.2") @DecimalMin(value = "0", inclusive = false) @Digits(integer = 5, fraction = 3)
        BigDecimal realWeightKg,
        @Size(max = 100) String internationalTrackingNumber,
        List<@Valid ItemChange> items) {

    public record ItemChange(
            @NotNull UUID itemId,
            @DecimalMin(value = "0", inclusive = false) @Digits(integer = 10, fraction = 2) BigDecimal actualPriceCny) {
    }
}
