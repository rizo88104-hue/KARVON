package uz.karvon.order.api.dto;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * Something that changed in a selected cart line when the prices were refreshed before ordering.
 * Sent in the "changes" field of the 409 CART_CHANGED answer.
 */
public record CartChange(UUID cartItemId, String title, Type change, BigDecimal oldPriceCny, BigDecimal newPriceCny) {

    public enum Type {
        PRICE_CHANGED, OUT_OF_STOCK, BLOCKED, REMOVED
    }
}
