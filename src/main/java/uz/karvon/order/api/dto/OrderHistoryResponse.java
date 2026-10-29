package uz.karvon.order.api.dto;

import uz.karvon.order.domain.Actor;
import uz.karvon.order.domain.OrderStatus;

import java.time.Instant;
import java.util.UUID;

public record OrderHistoryResponse(OrderStatus fromStatus, OrderStatus toStatus, UUID changedByUserId,
                                   Actor changedByRole, String comment, Instant createdAt) {
}
