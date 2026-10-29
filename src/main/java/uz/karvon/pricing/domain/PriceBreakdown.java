package uz.karvon.pricing.domain;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * The price of one cart line in UZS. Every part is rounded to 100 so'm and {@code totalUzs} is their sum.
 * {@code chargeableWeightKg} is for the whole line: max(minWeightKg, weightKg) * quantity.
 */
public record PriceBreakdown(
        BigDecimal goodsUzs,
        BigDecimal serviceFeeUzs,
        BigDecimal shippingUzs,
        BigDecimal totalUzs,
        BigDecimal chargeableWeightKg,
        UUID settingsId) {
}
