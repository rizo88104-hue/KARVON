package uz.karvon.pricing.domain;

import java.math.BigDecimal;
import java.util.Objects;
import java.util.UUID;

/**
 * The numbers of one pricing settings version that the calculator needs.
 * {@code feePercent} 10 means 10%. UZS amounts per kg, weights in kg.
 */
public record PricingParameters(
        UUID settingsId,
        BigDecimal customerRate,
        BigDecimal feePercent,
        BigDecimal minServiceFee,
        BigDecimal shippingCostPerKg,
        BigDecimal ourMarkupPerKg,
        BigDecimal minWeightKg,
        BigDecimal defaultWeightKg) {

    public PricingParameters {
        Objects.requireNonNull(customerRate, "customerRate");
        Objects.requireNonNull(feePercent, "feePercent");
        Objects.requireNonNull(minServiceFee, "minServiceFee");
        Objects.requireNonNull(shippingCostPerKg, "shippingCostPerKg");
        Objects.requireNonNull(ourMarkupPerKg, "ourMarkupPerKg");
        Objects.requireNonNull(minWeightKg, "minWeightKg");
        Objects.requireNonNull(defaultWeightKg, "defaultWeightKg");
    }

    /** What the customer pays per chargeable kg: the cargo cost plus our markup. */
    public BigDecimal shippingPricePerKg() {
        return shippingCostPerKg.add(ourMarkupPerKg);
    }
}
