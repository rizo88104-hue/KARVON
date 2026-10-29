package uz.karvon.settings.api.dto;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * The official CNY rate next to ours, for reference only.
 * {@code differencePercent} = (customerRate - cbuRate) / cbuRate * 100.
 */
public record CbuRateResponse(
        String currency,
        BigDecimal cbuRate,
        LocalDate date,
        BigDecimal customerRate,
        BigDecimal differencePercent) {
}
