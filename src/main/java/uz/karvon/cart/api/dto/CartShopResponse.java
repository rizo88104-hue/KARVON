package uz.karvon.cart.api.dto;

import uz.karvon.cart.domain.CartTotals;

import java.util.List;

/**
 * The lines from one shop; {@code totals} counts only the selected, available lines.
 */
public record CartShopResponse(String shopName, List<CartItemResponse> items, CartTotals totals) {
}
