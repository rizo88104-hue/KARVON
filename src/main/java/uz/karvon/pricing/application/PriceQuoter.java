package uz.karvon.pricing.application;

import uz.karvon.pricing.domain.PriceBreakdown;
import uz.karvon.pricing.domain.PriceCalculator;
import uz.karvon.pricing.domain.PricingParameters;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * Prices many items with one settings version, e.g. a page of search results.
 * Get one from {@link PricingService#quoter()}.
 */
public final class PriceQuoter {

    private final PriceCalculator calculator;
    private final PricingParameters parameters;

    PriceQuoter(PriceCalculator calculator, PricingParameters parameters) {
        this.calculator = calculator;
        this.parameters = parameters;
    }

    /** {@code weightKg} null means the default weight; {@code chinaShippingCny} null means 0. */
    public PriceBreakdown price(BigDecimal priceCny, BigDecimal chinaShippingCny, int quantity, BigDecimal weightKg) {
        return calculator.calculate(priceCny, chinaShippingCny, quantity, weightKg, parameters);
    }

    /** UZS for 1 CNY in these settings, e.g. to turn a UZS price filter into CNY. */
    public BigDecimal customerRate() {
        return parameters.customerRate();
    }

    /** The pricing settings version used by this quoter. */
    public UUID settingsId() {
        return parameters.settingsId();
    }
}
