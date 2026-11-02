package uz.karvon.order.application;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * The customer accepted a higher Taobao price: {@code amountUzs} more must be paid. Published inside the order transaction.
 */
public record PriceDifferenceDueEvent(UUID orderId, String number, UUID customerId, BigDecimal amountUzs) {
}
