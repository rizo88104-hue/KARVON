package uz.karvon.catalog.domain;

import java.math.BigDecimal;
import java.util.List;

/**
 * One buyable variant, e.g. black / size 42. {@code imageUrl} may be null.
 */
public record Sku(String skuId, List<TranslatedPair> properties, BigDecimal priceCny, int stock, String imageUrl) {

    public Sku withProperties(List<TranslatedPair> translated) {
        return new Sku(skuId, translated, priceCny, stock, imageUrl);
    }
}
