package uz.karvon.payment.application;

import uz.karvon.order.application.PaymentPurpose;
import uz.karvon.payment.domain.PaymentProvider;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * A payment was performed and the order updated. Published inside that transaction.
 */
public record PaymentCompletedEvent(UUID transactionId, UUID orderId, String orderNumber, UUID customerId,
                                    PaymentPurpose purpose, PaymentProvider provider, BigDecimal amountUzs,
                                    Instant performedAt) {
}
