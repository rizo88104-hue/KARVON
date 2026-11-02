package uz.karvon.order.application;

import uz.karvon.order.domain.Actor;
import uz.karvon.order.domain.OrderStatus;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * Published inside the transaction of every status change (also "created", with {@code from} null),
 * e.g. for notifications. {@code details} is what the order knew right after the change.
 */
public record OrderStatusChangedEvent(UUID orderId, String number, UUID customerId, OrderStatus from, OrderStatus to,
                                      Actor actor, Instant at, Details details) {

    /** Parcel data the customer is told about; null when not known yet. */
    public record Details(BigDecimal realWeightKg, String internationalTrackingNumber, String pickupInfo) {
        public static final Details NONE = new Details(null, null, null);
    }
}
