package uz.karvon.order.api.dto;

import uz.karvon.order.domain.OrderStatus;

import java.util.UUID;

/**
 * The result for one order of a bulk change: {@code status} when it moved, {@code errorCode} when not.
 */
public record BulkStatusResult(UUID orderId, boolean changed, OrderStatus status, String errorCode) {
}
