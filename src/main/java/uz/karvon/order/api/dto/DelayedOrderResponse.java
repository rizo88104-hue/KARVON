package uz.karvon.order.api.dto;

import uz.karvon.order.domain.OrderStatus;

import java.time.Instant;
import java.util.UUID;

/**
 * An order over its SLA: in {@code status} since {@code inStatusSince}, {@code overdueHours} past the limit.
 */
public record DelayedOrderResponse(UUID orderId, String number, OrderStatus status, Instant inStatusSince,
                                   long hoursInStatus, long limitHours, long overdueHours, UUID buyerId) {
}
