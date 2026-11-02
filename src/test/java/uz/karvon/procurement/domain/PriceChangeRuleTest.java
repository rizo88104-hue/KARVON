package uz.karvon.procurement.domain;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;

class PriceChangeRuleTest {

    @ParameterizedTest(name = "{0}: {1} -> {2} at {3}%")
    @CsvSource(delimiter = '|', textBlock = """
            # case                         | ordered | actual | threshold | ask
            same price                     | 89      | 89     | 3         | false
            cheaper                        | 89      | 80     | 3         | false
            rise below threshold           | 89      | 91     | 3         | false
            rise exactly at threshold      | 100     | 103    | 3         | false
            rise just above threshold      | 100     | 103.01 | 3         | true
            rise well above                | 89      | 92     | 3         | true
            zero threshold, any rise       | 89      | 89.01  | 0         | true
            higher threshold allows more   | 100     | 109    | 10        | false
            fractional prices              | 29.90   | 30.80  | 3         | true
            """)
    void asksOnlyAboveTheThreshold(String name, String ordered, String actual, String threshold, boolean ask) {
        assertThat(PriceChangeRule.needsCustomerDecision(new BigDecimal(ordered), new BigDecimal(actual),
                new BigDecimal(threshold))).isEqualTo(ask);
    }
}
