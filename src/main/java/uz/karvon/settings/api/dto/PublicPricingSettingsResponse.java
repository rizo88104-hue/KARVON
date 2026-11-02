package uz.karvon.settings.api.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * What customers may see. The shipping cost and our markup are shown only as their sum
 * ({@code shippingPricePerKg}), so our margin is not public. Delivery takes {@code deliveryDaysMin}–{@code deliveryDaysMax} days.
 */
public record PublicPricingSettingsResponse(
        UUID id,
        BigDecimal customerRate,
        BigDecimal feePercent,
        BigDecimal minServiceFee,
        BigDecimal shippingPricePerKg,
        BigDecimal minWeightKg,
        BigDecimal defaultWeightKg,
        int deliveryDaysMin,
        int deliveryDaysMax,
        Instant effectiveFrom) {
}
