package uz.karvon.order.api.dto;

import uz.karvon.order.domain.OrderStatus;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * A row of the order list.
 */
public record OrderSummaryResponse(
        String number,
        OrderStatus status,
        BigDecimal totalUzs,
        BigDecimal amountDueUzs,
        int itemCount,
        String firstItemTitle,
        String firstItemImageUrl,
        Instant createdAt) {
}
