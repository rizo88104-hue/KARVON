package uz.karvon.settings.api.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * All pricing settings of one version, for staff. {@code feePercent} 10 means 10%.
 */
public record PricingSettingsResponse(
        UUID id,
        BigDecimal customerRate,
        BigDecimal feePercent,
        BigDecimal minServiceFee,
        BigDecimal shippingCostPerKg,
        BigDecimal ourMarkupPerKg,
        BigDecimal minWeightKg,
        BigDecimal defaultWeightKg,
        BigDecimal priceChangeThresholdPercent,
        Instant effectiveFrom,
        UUID changedBy) {
}
