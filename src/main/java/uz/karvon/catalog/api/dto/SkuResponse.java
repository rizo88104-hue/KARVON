package uz.karvon.catalog.api.dto;

import uz.karvon.catalog.domain.TranslatedPair;
import uz.karvon.pricing.domain.PriceBreakdown;

import java.math.BigDecimal;
import java.util.List;

/**
 * A variant with its price for one item.
 */
public record SkuResponse(
        String skuId,
        List<TranslatedPair> properties,
        BigDecimal priceCny,
        int stock,
        String imageUrl,
        PriceBreakdown price) {
}
