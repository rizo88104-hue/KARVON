package uz.karvon.payment.infrastructure;

import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;
import uz.karvon.payment.application.PaymentGateway;
import uz.karvon.payment.domain.PaymentAccount;
import uz.karvon.payment.domain.PaymentProvider;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * Dev and test: instead of Payme or Click, the checkout link is GET /api/v1/dev/pay/{transactionId} on this API;
 * opening it marks the payment as done.
 */
@Component
@Profile({"dev", "test"})
public class FakePaymentProvider implements PaymentGateway {

    private final PaymentProperties properties;

    public FakePaymentProvider(PaymentProperties properties) {
        this.properties = properties;
    }

    @Override
    public boolean simulated() {
        return true;
    }

    @Override
    public String checkoutUrl(PaymentProvider provider, PaymentAccount account, BigDecimal amountUzs, UUID pendingTransactionId) {
        String base = properties.publicBaseUrl().endsWith("/")
                ? properties.publicBaseUrl().substring(0, properties.publicBaseUrl().length() - 1)
                : properties.publicBaseUrl();
        return base + "/api/v1/dev/pay/" + pendingTransactionId;
    }
}
