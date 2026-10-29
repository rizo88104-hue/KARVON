package uz.karvon.payment.application;

import uz.karvon.payment.domain.PaymentAccount;
import uz.karvon.payment.domain.PaymentProvider;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * Builds the page where the customer pays. Real: Payme and Click checkout links (the provider then calls our
 * Merchant/Shop API). Dev and test: a link to our own fake page that marks the payment as done.
 */
public interface PaymentGateway {

    /** True for the fake: the payment must be created on our side before the link is given. */
    boolean simulated();

    /** {@code pendingTransactionId} is set only when {@link #simulated()}. */
    String checkoutUrl(PaymentProvider provider, PaymentAccount account, BigDecimal amountUzs, UUID pendingTransactionId);
}
