package uz.karvon.order.application;

import java.math.BigDecimal;

/**
 * What customer notifications tell about an order. Any field may be null when not known yet.
 * {@code shippingDifferenceUzs} is positive when the customer must pay more, negative when it was refunded.
 */
public record OrderNotificationFacts(String number, BigDecimal realWeightKg, BigDecimal shippingDifferenceUzs,
                                     String trackingNumber, String pickupInfo) {

    public static OrderNotificationFacts unknown(String number) {
        return new OrderNotificationFacts(number, null, null, null, null);
    }
}
