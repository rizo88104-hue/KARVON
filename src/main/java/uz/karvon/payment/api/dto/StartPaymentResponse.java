package uz.karvon.payment.api.dto;

import uz.karvon.payment.domain.PaymentProvider;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * Open {@code checkoutUrl} to pay (PAYME, CLICK). For WALLET the payment is already done: {@code paid} is true.
 * {@code transactionId} is set when the payment was created on our side (wallet, dev fake).
 */
public record StartPaymentResponse(PaymentProvider provider, BigDecimal amountUzs, String checkoutUrl, boolean paid,
                                   UUID transactionId) {
}
