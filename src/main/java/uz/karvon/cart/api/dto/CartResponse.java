package uz.karvon.cart.api.dto;

import uz.karvon.cart.domain.CartTotals;

import java.util.List;
import java.util.UUID;

/**
 * The cart grouped by shop. {@code totals} counts only selected lines that are in stock and not blocked.
 * {@code settingsId} is the pricing settings version used for every price here.
 */
public record CartResponse(List<CartShopResponse> shops, CartTotals totals, int itemCount, int selectedCount,
                           UUID settingsId) {
}
