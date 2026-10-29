package uz.karvon.common.domain;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.util.Locale;
import java.util.Objects;

/**
 * Helpers for UZS amounts. UZS is always rounded to 100 so'm with HALF_UP.
 */
public final class Money {

    private static final int UZS_SCALE = -2;

    private Money() {
    }

    /** Rounds an amount to the nearest 100 so'm (HALF_UP), e.g. 1 234 550 -> 1 234 600. */
    public static BigDecimal roundUzs(BigDecimal amount) {
        Objects.requireNonNull(amount, "amount");
        return amount.setScale(UZS_SCALE, RoundingMode.HALF_UP).setScale(0, RoundingMode.UNNECESSARY);
    }

    /** Rounds to 100 so'm and formats for people, e.g. "1 234 500 so'm". */
    public static String formatUzs(BigDecimal amount) {
        return uzsFormat().format(roundUzs(amount)) + " so'm";
    }

    // DecimalFormat is not thread-safe, so a new one is made per call.
    private static DecimalFormat uzsFormat() {
        DecimalFormatSymbols symbols = new DecimalFormatSymbols(Locale.ROOT);
        symbols.setGroupingSeparator(' ');
        return new DecimalFormat("#,##0", symbols);
    }
}
