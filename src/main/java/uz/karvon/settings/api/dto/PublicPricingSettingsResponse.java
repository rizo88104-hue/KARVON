package uz.karvon.settings.api.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * What customers may see. The shipping cost and our markup are shown only as their sum
 * ({@code shippingPricePerKg}), so our margin is not public.
 */
public record PublicPricingSettingsResponse(
        UUID id,
        BigDecimal customerRate,
        BigDecimal feePercent,
        BigDecimal minServiceFee,
        BigDecimal shippingPricePerKg,
        BigDecimal minWeightKg,
        BigDecimal defaultWeightKg,
        Instant effectiveFrom) {
}
