package uz.karvon.pricing.domain;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.math.BigDecimal;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

class PriceCalculatorTest {

    /** The seed settings: rate 1780, fee 10%, min fee 15 000, 70 000 + 10 000 per kg, min/default weight 0.5. */
    static final PricingParameters SEED = parameters("1780", "10", "15000", "0.5", "0.5");

    private final PriceCalculator calculator = new PriceCalculator();

    @ParameterizedTest(name = "{0}")
    @CsvSource(delimiter = '|', textBlock = """
            # case                            | price  | chinaShip | qty | weight | goods    | fee    | shipping | total    | chargeable
            percent fee applies               | 129    | 0         | 1   | 0.9    | 229600   | 23000  | 72000    | 324600   | 0.900
            min fee applies                   | 20     | 0         | 1   | 0.9    | 35600    | 15000  | 72000    | 122600   | 0.900
            fee exactly at the minimum        | 84.27  | 0         | 1   | 1      | 150000   | 15000  | 80000    | 245000   | 1.000
            min weight applies                | 129    | 0         | 1   | 0.2    | 229600   | 23000  | 40000    | 292600   | 0.500
            unknown weight uses default       | 129    | 0         | 1   |        | 229600   | 23000  | 40000    | 292600   | 0.500
            quantity multiplies everything    | 129    | 0         | 3   | 0.9    | 688900   | 68900  | 216000   | 973800   | 2.700
            quantity with min weight          | 10     | 0         | 4   | 0.1    | 71200    | 15000  | 160000   | 246200   | 2.000
            china shipping is added per item  | 100    | 8         | 2   | 1.2    | 384500   | 38500  | 192000   | 615000   | 2.400
            goods rounded down below 50       | 1.02   | 0         | 1   | 0.5    | 1800     | 15000  | 40000    | 56800    | 0.500
            goods rounded up from 50          | 0.88   | 0         | 1   | 0.5    | 1600     | 15000  | 40000    | 56600    | 0.500
            fee rounded half up               | 140.17 | 0         | 1   | 0.5    | 249500   | 25000  | 40000    | 314500   | 0.500
            shipping rounded                  | 129    | 0         | 1   | 0.9999 | 229600   | 23000  | 80000    | 332600   | 1.000
            shipping rounded down             | 129    | 0         | 1   | 0.5006 | 229600   | 23000  | 40000    | 292600   | 0.501
            """)
    void calculatesEveryPart(String name, String price, String chinaShip, int quantity, String weight,
                             String goods, String fee, String shipping, String total, String chargeable) {
        PriceBreakdown result = calculator.calculate(new BigDecimal(price), new BigDecimal(chinaShip), quantity,
                weight == null ? null : new BigDecimal(weight), SEED);

        assertThat(result.goodsUzs()).isEqualByComparingTo(goods);
        assertThat(result.serviceFeeUzs()).isEqualByComparingTo(fee);
        assertThat(result.shippingUzs()).isEqualByComparingTo(shipping);
        assertThat(result.totalUzs()).isEqualByComparingTo(total);
        assertThat(result.chargeableWeightKg()).isEqualByComparingTo(chargeable);
    }

    @ParameterizedTest(name = "fee {0}% with min {1}")
    @CsvSource({
            "10, 15000, 23000",
            "5,  15000, 15000",
            "5,  5000,  11500",
            "0,  0,     0",
            "0,  15000, 15000",
            "12.5, 0,   28700"
    })
    void serviceFeeIsTheBiggerOfPercentAndMinimum(String feePercent, String minFee, String expectedFee) {
        PricingParameters parameters = parameters("1780", feePercent, minFee, "0.5", "0.5");

        PriceBreakdown result = calculator.calculate(new BigDecimal("129"), BigDecimal.ZERO, 1, BigDecimal.ONE, parameters);

        assertThat(result.serviceFeeUzs()).isEqualByComparingTo(expectedFee);
    }

    @Test
    void partsAreWholeHundredsAndAddUp() {
        PriceBreakdown result = calculator.calculate(new BigDecimal("77.77"), new BigDecimal("3.33"), 7,
                new BigDecimal("0.333"), SEED);

        for (BigDecimal part : new BigDecimal[]{result.goodsUzs(), result.serviceFeeUzs(), result.shippingUzs()}) {
            assertThat(part.remainder(BigDecimal.valueOf(100))).isEqualByComparingTo("0");
            assertThat(part.scale()).isZero();
        }
        assertThat(result.totalUzs())
                .isEqualTo(result.goodsUzs().add(result.serviceFeeUzs()).add(result.shippingUzs()));
    }

    @Test
    void missingChinaShippingIsZeroAndSettingsIdIsKept() {
        PriceBreakdown result = calculator.calculate(new BigDecimal("129"), null, 1, new BigDecimal("0.9"), SEED);

        assertThat(result.goodsUzs()).isEqualByComparingTo("229600");
        assertThat(result.settingsId()).isEqualTo(SEED.settingsId());
    }

    @Test
    void rejectsInvalidInput() {
        assertThatIllegalArgumentException()
                .isThrownBy(() -> calculator.calculate(BigDecimal.TEN, BigDecimal.ZERO, 0, null, SEED));
        assertThatIllegalArgumentException()
                .isThrownBy(() -> calculator.calculate(new BigDecimal("-1"), BigDecimal.ZERO, 1, null, SEED));
        assertThatIllegalArgumentException()
                .isThrownBy(() -> calculator.calculate(BigDecimal.TEN, BigDecimal.ZERO, 1, BigDecimal.ZERO, SEED));
    }

    static PricingParameters parameters(String rate, String feePercent, String minFee, String minWeight,
                                        String defaultWeight) {
        return new PricingParameters(UUID.randomUUID(), new BigDecimal(rate), new BigDecimal(feePercent),
                new BigDecimal(minFee), new BigDecimal("70000"), new BigDecimal("10000"),
                new BigDecimal(minWeight), new BigDecimal(defaultWeight));
    }
}
