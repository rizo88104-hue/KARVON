package uz.karvon.order.application;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * The parcel was weighed and the real shipping is higher than prepaid: the customer must pay {@code amountUzs}.
 * Published inside the order transaction.
 */
public record ShippingDifferenceDueEvent(UUID orderId, String number, UUID customerId, BigDecimal amountUzs) {
}
