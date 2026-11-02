package uz.karvon.admin.domain;

import uz.karvon.common.domain.Money;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Objects;

/**
 * Money of one order for reports. All UZS results are rounded to 100 so'm (HALF_UP).
 * <ul>
 * <li>paid = paid - refunded (what we kept);</li>
 * <li>goods cost = what the buyer paid on Taobao in CNY times the customer rate on the purchase day
 *     (computed by the report query, rounded here);</li>
 * <li>shipping charged = the final shipping, or the prepaid one while the real weight is unknown; our markup in it
 *     is shipping * ourMarkupPerKg / (shippingCostPerKg + ourMarkupPerKg), the rest is the cargo cost;</li>
 * <li>the cargo cost counts only for paid, not cancelled orders (nothing is shipped otherwise);</li>
 * <li>profit = paid - goods cost - cargo cost.</li>
 * </ul>
 */
public record OrderProfit(BigDecimal paidUzs, BigDecimal goodsCostUzs, BigDecimal shippingCostUzs,
                          BigDecimal serviceFeeUzs, BigDecimal profitUzs) {

    private static final int DIVISION_SCALE = 10;

    /**
     * @param shipped false for unpaid or cancelled orders: no cargo cost
     */
    public static OrderProfit of(BigDecimal netPaidUzs, BigDecimal goodsCostUzs, BigDecimal serviceFeeUzs,
                                 BigDecimal shippingChargedUzs, BigDecimal shippingCostPerKg, BigDecimal ourMarkupPerKg,
                                 boolean shipped) {
        Objects.requireNonNull(netPaidUzs, "netPaidUzs");
        Objects.requireNonNull(goodsCostUzs, "goodsCostUzs");
        Objects.requireNonNull(serviceFeeUzs, "serviceFeeUzs");
        Objects.requireNonNull(shippingChargedUzs, "shippingChargedUzs");
        BigDecimal goods = Money.roundUzs(goodsCostUzs);
        BigDecimal cargo = shipped
                ? shippingChargedUzs.subtract(shippingMarkup(shippingChargedUzs, shippingCostPerKg, ourMarkupPerKg))
                : BigDecimal.ZERO;
        BigDecimal paid = Money.roundUzs(netPaidUzs);
        return new OrderProfit(paid, goods, cargo, serviceFeeUzs, paid.subtract(goods).subtract(cargo));
    }

    /** Our markup part of a shipping amount, rounded to 100 so'm; 0 when both per-kg prices are 0. */
    public static BigDecimal shippingMarkup(BigDecimal shippingUzs, BigDecimal shippingCostPerKg, BigDecimal ourMarkupPerKg) {
        BigDecimal perKg = shippingCostPerKg.add(ourMarkupPerKg);
        if (perKg.signum() == 0) {
            return BigDecimal.ZERO;
        }
        return Money.roundUzs(shippingUzs.multiply(ourMarkupPerKg).divide(perKg, DIVISION_SCALE, RoundingMode.HALF_UP));
    }
}
