package uz.karvon.cart.domain;

import uz.karvon.pricing.domain.PriceBreakdown;

import java.math.BigDecimal;

/**
 * Sum of line prices. Every line is priced on its own with the CLAUDE.md formula (so the minimum service fee
 * and the minimum weight apply per line), and the totals are the sums of the line parts.
 *
 * @param lines    number of cart lines counted
 * @param quantity number of pieces in those lines
 */
public record CartTotals(BigDecimal goodsUzs, BigDecimal serviceFeeUzs, BigDecimal shippingUzs, BigDecimal totalUzs,
                         int lines, int quantity) {

    public static final CartTotals EMPTY =
            new CartTotals(BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, 0, 0);

    public CartTotals plus(PriceBreakdown line, int lineQuantity) {
        return new CartTotals(goodsUzs.add(line.goodsUzs()), serviceFeeUzs.add(line.serviceFeeUzs()),
                shippingUzs.add(line.shippingUzs()), totalUzs.add(line.totalUzs()), lines + 1, quantity + lineQuantity);
    }
}
