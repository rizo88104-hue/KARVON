package uz.karvon.payment.domain;

import uz.karvon.order.application.PaymentPurpose;

import java.util.Optional;

/**
 * The account string given to Payme (account.order) and Click (merchant_trans_id / transaction_param):
 * the order number for the order, the order number + {@value #SHIPPING_SUFFIX} for its shipping difference,
 * e.g. "KRV-000123" and "KRV-000123-SHIP".
 */
public record PaymentAccount(String orderNumber, PaymentPurpose purpose) {

    public static final String SHIPPING_SUFFIX = "-SHIP";

    public String value() {
        return purpose == PaymentPurpose.ORDER ? orderNumber : orderNumber + SHIPPING_SUFFIX;
    }

    public static Optional<PaymentAccount> parse(String value) {
        if (value == null || value.isBlank()) {
            return Optional.empty();
        }
        String trimmed = value.trim();
        if (trimmed.endsWith(SHIPPING_SUFFIX)) {
            return Optional.of(new PaymentAccount(trimmed.substring(0, trimmed.length() - SHIPPING_SUFFIX.length()),
                    PaymentPurpose.SHIPPING_DIFFERENCE));
        }
        return Optional.of(new PaymentAccount(trimmed, PaymentPurpose.ORDER));
    }
}
