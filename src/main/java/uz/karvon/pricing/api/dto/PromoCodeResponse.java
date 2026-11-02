package uz.karvon.pricing.api.dto;

import uz.karvon.pricing.domain.PromoCodeType;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * A promo code; {@code usedCount} is the number of orders that use it now.
 */
public record PromoCodeResponse(UUID id, String code, PromoCodeType type, BigDecimal value, Integer maxUses,
                                Integer perUserLimit, Instant validFrom, Instant validTo, boolean active,
                                int usedCount, Instant createdAt, Instant updatedAt) {
}
