package uz.karvon.common.domain;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;

class MoneyTest {

    @ParameterizedTest
    @CsvSource({
            "1234549,     1234500",
            "1234550,     1234600",
            "1234550.01,  1234600",
            "1234449.99,  1234400",
            "49.99,       0",
            "50,          100",
            "0,           0",
            "15000,       15000",
            "-1234550,    -1234600"
    })
    void roundsUzsTo100SomHalfUp(String amount, String expected) {
        assertThat(Money.roundUzs(new BigDecimal(amount))).isEqualTo(new BigDecimal(expected));
    }

    @Test
    void roundedValueHasNoFractionDigits() {
        assertThat(Money.roundUzs(new BigDecimal("1234.56")).scale()).isZero();
    }

    @Test
    void formatsWithSpacesAndCurrency() {
        assertThat(Money.formatUzs(new BigDecimal("1234500"))).isEqualTo("1 234 500 so'm");
        assertThat(Money.formatUzs(new BigDecimal("1234549.99"))).isEqualTo("1 234 500 so'm");
        assertThat(Money.formatUzs(new BigDecimal("15000"))).isEqualTo("15 000 so'm");
        assertThat(Money.formatUzs(BigDecimal.ZERO)).isEqualTo("0 so'm");
    }

    @Test
    void rejectsNull() {
        assertThatNullPointerException().isThrownBy(() -> Money.roundUzs(null));
    }
}
