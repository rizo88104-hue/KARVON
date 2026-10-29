package uz.karvon.cart.domain;

import org.junit.jupiter.api.Test;
import uz.karvon.pricing.domain.PriceBreakdown;

import java.math.BigDecimal;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class CartTotalsTest {

    private static final UUID SETTINGS = UUID.randomUUID();

    @Test
    void emptyIsZero() {
        assertThat(CartTotals.EMPTY.totalUzs()).isEqualByComparingTo("0");
        assertThat(CartTotals.EMPTY.lines()).isZero();
    }

    @Test
    void addsEveryPartOfEveryLine() {
        CartTotals totals = CartTotals.EMPTY
                .plus(line("459200", "45900", "144000"), 2)
                .plus(line("158400", "15800", "64000"), 1);

        assertThat(totals.goodsUzs()).isEqualByComparingTo("617600");
        assertThat(totals.serviceFeeUzs()).isEqualByComparingTo("61700");
        assertThat(totals.shippingUzs()).isEqualByComparingTo("208000");
        assertThat(totals.totalUzs()).isEqualByComparingTo("887300");
        assertThat(totals.lines()).isEqualTo(2);
        assertThat(totals.quantity()).isEqualTo(3);
    }

    @Test
    void minimumFeeStaysPerLine() {
        // Two cheap lines each pay the 15 000 minimum (per-line rule).
        CartTotals totals = CartTotals.EMPTY
                .plus(line("35600", "15000", "40000"), 1)
                .plus(line("53400", "15000", "40000"), 1);

        assertThat(totals.serviceFeeUzs()).isEqualByComparingTo("30000");
        assertThat(totals.totalUzs()).isEqualByComparingTo("199000");
    }

    private static PriceBreakdown line(String goods, String fee, String shipping) {
        BigDecimal total = new BigDecimal(goods).add(new BigDecimal(fee)).add(new BigDecimal(shipping));
        return new PriceBreakdown(new BigDecimal(goods), new BigDecimal(fee), new BigDecimal(shipping), total,
                BigDecimal.ONE, SETTINGS);
    }
}
