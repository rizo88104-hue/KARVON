package uz.karvon.admin.domain;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;

class OrderProfitTest {

    private static final BigDecimal COST_PER_KG = new BigDecimal("70000");
    private static final BigDecimal MARKUP_PER_KG = new BigDecimal("10000");

    @Test
    void profitIsPaidMinusGoodsCostMinusCargoCost() {
        // 1 kg shipped for 80 000: 10 000 is our markup, 70 000 the cargo cost.
        OrderProfit profit = OrderProfit.of(bd("278000"), bd("210000"), bd("18000"), bd("80000"), COST_PER_KG,
                MARKUP_PER_KG, true);

        assertThat(profit.paidUzs()).isEqualByComparingTo("278000");
        assertThat(profit.goodsCostUzs()).isEqualByComparingTo("210000");
        assertThat(profit.shippingCostUzs()).isEqualByComparingTo("70000");
        assertThat(profit.serviceFeeUzs()).isEqualByComparingTo("18000");
        assertThat(profit.profitUzs()).isEqualByComparingTo("-2000");
    }

    @Test
    void goodsCostIsRoundedTo100Som() {
        OrderProfit profit = OrderProfit.of(bd("100000"), bd("50049.99"), bd("15000"), bd("0"), COST_PER_KG,
                MARKUP_PER_KG, true);

        assertThat(profit.goodsCostUzs()).isEqualByComparingTo("50000");
        assertThat(OrderProfit.of(bd("100000"), bd("50050"), bd("15000"), bd("0"), COST_PER_KG, MARKUP_PER_KG, true)
                .goodsCostUzs()).isEqualByComparingTo("50100");
    }

    @Test
    void unpaidOrCancelledOrdersHaveNoCargoCost() {
        OrderProfit cancelled = OrderProfit.of(bd("0"), bd("0"), bd("15000"), bd("40000"), COST_PER_KG, MARKUP_PER_KG,
                false);

        assertThat(cancelled.shippingCostUzs()).isEqualByComparingTo("0");
        assertThat(cancelled.profitUzs()).isEqualByComparingTo("0");
    }

    @Test
    void shippingMarkupIsTheMarkupShareRoundedTo100Som() {
        assertThat(OrderProfit.shippingMarkup(bd("40000"), COST_PER_KG, MARKUP_PER_KG)).isEqualByComparingTo("5000");
        // 64 000 * 1/8 = 8 000; 30 000 * 1/8 = 3 750 -> 3 800
        assertThat(OrderProfit.shippingMarkup(bd("64000"), COST_PER_KG, MARKUP_PER_KG)).isEqualByComparingTo("8000");
        assertThat(OrderProfit.shippingMarkup(bd("30000"), COST_PER_KG, MARKUP_PER_KG)).isEqualByComparingTo("3800");
        assertThat(OrderProfit.shippingMarkup(bd("30000"), bd("0"), bd("0"))).isEqualByComparingTo("0");
    }

    private static BigDecimal bd(String value) {
        return new BigDecimal(value);
    }
}
