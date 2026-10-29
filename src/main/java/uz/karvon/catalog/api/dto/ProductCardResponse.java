package uz.karvon.catalog.api.dto;

import java.math.BigDecimal;

/**
 * A search result card. {@code priceUzs} is the full price of one item (goods, service fee and shipping
 * with the default weight), from the price calculator.
 */
public record ProductCardResponse(
        String externalId,
        String title,
        String titleOriginal,
        String mainImageUrl,
        BigDecimal priceCny,
        BigDecimal priceUzs,
        long salesCount,
        String shopName) {
}
