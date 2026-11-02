package uz.karvon.order.application;

import java.util.UUID;

/**
 * A new order with payment method WALLET: the payment module pays it from the wallet at once, in the same
 * transaction as the order creation, so a failed payment also undoes the order.
 */
public record OrderWalletPaymentRequestedEvent(UUID orderId, String number, UUID customerId) {
}
