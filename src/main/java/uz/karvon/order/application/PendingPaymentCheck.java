package uz.karvon.order.application;

import java.util.UUID;

/**
 * Asked before an unpaid order is cancelled automatically: a payment that has started at a provider
 * (e.g. Payme keeps a created transaction open up to 12 hours) must be allowed to finish.
 * Implemented by the payment module.
 */
public interface PendingPaymentCheck {

    boolean hasPendingPayment(UUID orderId);
}
