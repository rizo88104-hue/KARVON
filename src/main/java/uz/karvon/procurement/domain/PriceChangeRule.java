package uz.karvon.procurement.domain;

import java.math.BigDecimal;
import java.math.MathContext;
import java.util.Objects;

/**
 * Whether a Taobao price rise is big enough to ask the customer: the actual price is higher than the price at
 * order time by MORE than {@code thresholdPercent} percent. Rises up to the threshold, and cheaper prices, are taken
 * as they are.
 */
public final class PriceChangeRule {

    private static final BigDecimal HUNDRED = BigDecimal.valueOf(100);

    private PriceChangeRule() {
    }

    public static boolean needsCustomerDecision(BigDecimal orderedPriceCny, BigDecimal actualPriceCny,
                                                BigDecimal thresholdPercent) {
        Objects.requireNonNull(orderedPriceCny, "orderedPriceCny");
        Objects.requireNonNull(actualPriceCny, "actualPriceCny");
        Objects.requireNonNull(thresholdPercent, "thresholdPercent");
        if (orderedPriceCny.signum() <= 0) {
            return actualPriceCny.compareTo(orderedPriceCny) > 0;
        }
        if (actualPriceCny.compareTo(orderedPriceCny) <= 0) {
            return false;
        }
        BigDecimal risePercent = actualPriceCny.subtract(orderedPriceCny).multiply(HUNDRED)
                .divide(orderedPriceCny, MathContext.DECIMAL64);
        return risePercent.compareTo(thresholdPercent) > 0;
    }
}
