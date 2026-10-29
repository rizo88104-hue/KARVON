package uz.karvon.payment.infrastructure;

import java.math.BigDecimal;

/**
 * Payme amounts are whole tiyin (1 so'm = 100 tiyin).
 */
public final class PaymeAmounts {

    private static final BigDecimal TIYIN_PER_SOM = BigDecimal.valueOf(100);

    private PaymeAmounts() {
    }

    public static long toTiyin(BigDecimal uzs) {
        return uzs.multiply(TIYIN_PER_SOM).longValueExact();
    }

    public static BigDecimal toUzs(long tiyin) {
        return BigDecimal.valueOf(tiyin).divide(TIYIN_PER_SOM);
    }
}
