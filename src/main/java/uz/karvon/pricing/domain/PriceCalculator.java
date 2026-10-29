package uz.karvon.pricing.domain;

import uz.karvon.common.domain.Money;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Objects;

/**
 * The price formulas from CLAUDE.md, in plain Java:
 * <pre>
 * goods      = (priceCny + chinaShippingCny) * quantity * customerRate
 * serviceFee = max(goods * feePercent, minServiceFee)
 * shipping   = max(minWeightKg, weightKg) * quantity * (shippingCostPerKg + ourMarkupPerKg)
 * total      = goods + serviceFee + shipping
 * </pre>
 * Each part is rounded to 100 so'm (HALF_UP). The service fee is taken from the rounded goods amount,
 * so the parts the customer sees add up exactly. A missing weight means {@code defaultWeightKg}.
 */
public class PriceCalculator {

    static final int WEIGHT_SCALE = 3;

    /**
     * @param weightKg weight of one item; null when unknown
     */
    public PriceBreakdown calculate(BigDecimal priceCny, BigDecimal chinaShippingCny, int quantity,
                                    BigDecimal weightKg, PricingParameters parameters) {
        Objects.requireNonNull(priceCny, "priceCny");
        Objects.requireNonNull(parameters, "parameters");
        BigDecimal chinaShipping = chinaShippingCny == null ? BigDecimal.ZERO : chinaShippingCny;
        if (priceCny.signum() < 0 || chinaShipping.signum() < 0) {
            throw new IllegalArgumentException("Prices cannot be negative");
        }
        if (quantity < 1) {
            throw new IllegalArgumentException("Quantity must be at least 1");
        }
        if (weightKg != null && weightKg.signum() <= 0) {
            throw new IllegalArgumentException("Weight must be positive");
        }
        BigDecimal units = BigDecimal.valueOf(quantity);

        BigDecimal goods = Money.roundUzs(priceCny.add(chinaShipping).multiply(units).multiply(parameters.customerRate()));
        BigDecimal percentFee = goods.multiply(parameters.feePercent()).movePointLeft(2);
        BigDecimal serviceFee = Money.roundUzs(percentFee.max(parameters.minServiceFee()));

        BigDecimal weight = weightKg == null ? parameters.defaultWeightKg() : weightKg;
        BigDecimal chargeableWeight = parameters.minWeightKg().max(weight).multiply(units);
        BigDecimal shipping = shipping(chargeableWeight, parameters);

        return new PriceBreakdown(goods, serviceFee, shipping, goods.add(serviceFee).add(shipping),
                chargeableWeight.setScale(WEIGHT_SCALE, RoundingMode.HALF_UP), parameters.settingsId());
    }

    /** Shipping for an already chargeable weight, rounded to 100 so'm. */
    static BigDecimal shipping(BigDecimal chargeableWeightKg, PricingParameters parameters) {
        return Money.roundUzs(chargeableWeightKg.multiply(parameters.shippingPricePerKg()));
    }
}
