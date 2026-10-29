package uz.karvon.catalog.domain;

import java.math.BigDecimal;

/**
 * A search result. {@code titleTranslated} is null until the catalog service translates it.
 */
public record ProductSummary(
        String externalId,
        String titleOriginal,
        String titleTranslated,
        String mainImageUrl,
        BigDecimal priceCny,
        long salesCount,
        String shopName) {

    public ProductSummary withTitleTranslated(String title) {
        return new ProductSummary(externalId, titleOriginal, title, mainImageUrl, priceCny, salesCount, shopName);
    }
}
