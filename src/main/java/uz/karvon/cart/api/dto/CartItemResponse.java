package uz.karvon.cart.api.dto;

import uz.karvon.catalog.domain.TranslatedPair;
import uz.karvon.pricing.domain.PriceBreakdown;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

/**
 * A cart line with its price for the whole quantity, priced with the current settings.
 * After a price refresh: {@code priceChanged} with {@code previousPriceCny} (old) and {@code priceCny} (new);
 * {@code outOfStock} or {@code blocked} lines are not counted in the totals.
 */
public record CartItemResponse(
        UUID id,
        String externalProductId,
        String skuId,
        String title,
        String imageUrl,
        List<TranslatedPair> skuProperties,
        String shopName,
        BigDecimal priceCny,
        BigDecimal chinaShippingCny,
        BigDecimal weightKg,
        int quantity,
        String comment,
        boolean selected,
        boolean priceChanged,
        BigDecimal previousPriceCny,
        boolean outOfStock,
        boolean blocked,
        PriceBreakdown price) {
}
