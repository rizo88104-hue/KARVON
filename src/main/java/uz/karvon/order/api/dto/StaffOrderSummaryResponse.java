package uz.karvon.order.api.dto;

import uz.karvon.order.domain.OrderStatus;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record StaffOrderSummaryResponse(
        UUID id,
        String number,
        OrderStatus status,
        UUID customerId,
        String customerPhone,
        BigDecimal totalUzs,
        BigDecimal amountDueUzs,
        int itemCount,
        Instant createdAt) {
}
