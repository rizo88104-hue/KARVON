package uz.karvon.pricing.domain;

import java.math.BigDecimal;
import java.util.Objects;

/**
 * The difference between the shipping the customer prepaid and the shipping for the real weight.
 * {@code differenceUzs > 0}: the customer pays it (AWAITING_SHIPPING_PAYMENT);
 * {@code < 0}: it goes back to the customer's wallet; {@code 0}: nothing to do.
 *
 * @param realShippingUzs shipping for the real weight, rounded to 100 so'm
 */
public record ShippingAdjustment(BigDecimal prepaidShippingUzs, BigDecimal realShippingUzs, BigDecimal differenceUzs) {

    /**
     * Uses the same formula as {@link PriceCalculator}, with the settings the order was priced with.
     * {@code realWeightKg} is the weighed weight of the whole line (all units), so
     * max(minWeightKg, realWeightKg / quantity) * quantity = max(minWeightKg * quantity, realWeightKg).
     */
    public static ShippingAdjustment of(BigDecimal prepaidShippingUzs, BigDecimal realWeightKg, int quantity,
                                        PricingParameters parameters) {
        Objects.requireNonNull(prepaidShippingUzs, "prepaidShippingUzs");
        Objects.requireNonNull(realWeightKg, "realWeightKg");
        if (realWeightKg.signum() <= 0) {
            throw new IllegalArgumentException("Real weight must be positive");
        }
        if (quantity < 1) {
            throw new IllegalArgumentException("Quantity must be at least 1");
        }
        BigDecimal chargeable = parameters.minWeightKg().multiply(BigDecimal.valueOf(quantity)).max(realWeightKg);
        BigDecimal real = PriceCalculator.shipping(chargeable, parameters);
        return new ShippingAdjustment(prepaidShippingUzs, real, real.subtract(prepaidShippingUzs));
    }

    public boolean customerPays() {
        return differenceUzs.signum() > 0;
    }

    public boolean refundToWallet() {
        return differenceUzs.signum() < 0;
    }
}
