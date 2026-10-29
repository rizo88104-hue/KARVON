package uz.karvon.catalog.api.dto;

import uz.karvon.catalog.domain.ShopInfo;
import uz.karvon.catalog.domain.TranslatedPair;
import uz.karvon.pricing.domain.PriceBreakdown;

import java.math.BigDecimal;
import java.util.List;

/**
 * A product page. {@code price} is for the cheapest SKU. {@code weightEstimated} is true when Taobao gave
 * no weight and the default weight was used; the real weight is known only at the warehouse.
 */
public record ProductDetailResponse(
        String externalId,
        String title,
        String titleOriginal,
        String mainImageUrl,
        List<String> images,
        BigDecimal priceCny,
        PriceBreakdown price,
        long salesCount,
        ShopInfo shop,
        List<TranslatedPair> attributes,
        List<SkuResponse> skus,
        BigDecimal chinaShippingCny,
        BigDecimal weightKg,
        boolean weightEstimated) {
}
