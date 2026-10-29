package uz.karvon.order.api.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;

/**
 * What the buyer fills in while purchasing. A null field is left unchanged.
 */
public record UpdateOrderItemRequest(
        @DecimalMin(value = "0", inclusive = false) @Digits(integer = 10, fraction = 2) BigDecimal actualPriceCny,
        @Size(max = 100) String taobaoOrderNumber,
        @Size(max = 100) String chinaTrackingNumber) {
}
