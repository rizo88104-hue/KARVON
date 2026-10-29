package uz.karvon.order.application;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * Money to give back to the customer (to the wallet, Task 7). Published inside the order transaction;
 * the order already counts it in refundedUzs.
 */
public record OrderRefundRequestedEvent(UUID orderId, String number, UUID customerId, BigDecimal amountUzs,
                                        RefundReason reason) {
}
