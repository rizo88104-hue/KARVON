package uz.karvon.pricing.domain;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

class ShippingAdjustmentTest {

    private static final PricingParameters SEED = PriceCalculatorTest.SEED;

    @ParameterizedTest(name = "{0}")
    @CsvSource(delimiter = '|', textBlock = """
            # case                              | prepaid | realWeight | qty | realShipping | difference | pays  | refund
            heavier: customer pays difference   | 72000   | 1.4        | 1   | 112000       | 40000      | true  | false
            lighter: refund to wallet           | 72000   | 0.6        | 1   | 48000        | -24000     | false | true
            same weight: nothing                | 72000   | 0.9        | 1   | 72000        | 0          | false | false
            lighter than min weight: min used   | 72000   | 0.2        | 1   | 40000        | -32000     | false | true
            min weight applies per unit         | 160000  | 1.0        | 4   | 160000       | 0          | false | false
            line weight above min for 3 units   | 216000  | 3.1        | 3   | 248000       | 32000      | true  | false
            real shipping rounded to 100        | 40000   | 0.5006     | 1   | 40000        | 0          | false | false
            """)
    void comparesPrepaidWithRealShipping(String name, String prepaid, String realWeight, int quantity,
                                         String realShipping, String difference, boolean pays, boolean refund) {
        ShippingAdjustment adjustment = ShippingAdjustment.of(new BigDecimal(prepaid), new BigDecimal(realWeight),
                quantity, SEED);

        assertThat(adjustment.realShippingUzs()).isEqualByComparingTo(realShipping);
        assertThat(adjustment.differenceUzs()).isEqualByComparingTo(difference);
        assertThat(adjustment.customerPays()).isEqualTo(pays);
        assertThat(adjustment.refundToWallet()).isEqualTo(refund);
    }

    @Test
    void matchesTheCalculatorForTheSameWeight() {
        PriceBreakdown priced = new PriceCalculator().calculate(new BigDecimal("129"), BigDecimal.ZERO, 3,
                new BigDecimal("0.7"), SEED);

        ShippingAdjustment adjustment = ShippingAdjustment.of(priced.shippingUzs(), new BigDecimal("2.1"), 3, SEED);

        assertThat(adjustment.differenceUzs()).isEqualByComparingTo("0");
    }

    @Test
    void rejectsInvalidInput() {
        assertThatIllegalArgumentException()
                .isThrownBy(() -> ShippingAdjustment.of(BigDecimal.TEN, BigDecimal.ZERO, 1, SEED));
        assertThatIllegalArgumentException()
                .isThrownBy(() -> ShippingAdjustment.of(BigDecimal.TEN, BigDecimal.ONE, 0, SEED));
    }
}
