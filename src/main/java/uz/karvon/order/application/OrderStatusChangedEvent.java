package uz.karvon.order.application;

import uz.karvon.order.domain.Actor;
import uz.karvon.order.domain.OrderStatus;

import java.time.Instant;
import java.util.UUID;

/**
 * Published inside the transaction of every status change (also "created", with {@code from} null),
 * e.g. for notifications.
 */
public record OrderStatusChangedEvent(UUID orderId, String number, UUID customerId, OrderStatus from, OrderStatus to,
                                      Actor actor, Instant at) {
}
