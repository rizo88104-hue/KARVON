package uz.karvon.catalog.domain;

import java.math.BigDecimal;
import java.util.List;

/**
 * A product page. {@code priceCny} is the lowest SKU price; {@code weightKg} is null when Taobao does not say,
 * and then pricing uses the default weight. Every product has at least one SKU.
 */
public record ProductDetail(
        String externalId,
        String titleOriginal,
        String titleTranslated,
        String mainImageUrl,
        List<String> images,
        BigDecimal priceCny,
        long salesCount,
        ShopInfo shop,
        List<TranslatedPair> attributes,
        List<Sku> skus,
        BigDecimal chinaShippingCny,
        BigDecimal weightKg) {

    public ProductDetail translated(String title, List<TranslatedPair> translatedAttributes, List<Sku> translatedSkus) {
        return new ProductDetail(externalId, titleOriginal, title, mainImageUrl, images, priceCny, salesCount, shop,
                translatedAttributes, translatedSkus, chinaShippingCny, weightKg);
    }
}
