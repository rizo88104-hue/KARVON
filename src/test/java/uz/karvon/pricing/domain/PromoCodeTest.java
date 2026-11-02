package uz.karvon.pricing.domain;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

class PromoCodeTest {

    private static final Instant NOW = Instant.parse("2026-11-02T10:00:00Z");

    @Test
    void percentOfFeeIsRoundedTo100Som() {
        PromoCode half = promo(PromoCodeType.PERCENT_OF_FEE, "50");

        assertThat(half.discountFor(bd("23800"))).isEqualByComparingTo("11900");
        // 15% of 15 300 = 2 295 -> 2 300
        assertThat(promo(PromoCodeType.PERCENT_OF_FEE, "15").discountFor(bd("15300"))).isEqualByComparingTo("2300");
        assertThat(promo(PromoCodeType.PERCENT_OF_FEE, "100").discountFor(bd("15000"))).isEqualByComparingTo("15000");
    }

    @Test
    void theFeeNeverGoesBelowZero() {
        PromoCode fixed = promo(PromoCodeType.FIXED_UZS, "50000");

        assertThat(fixed.discountFor(bd("23800"))).isEqualByComparingTo("23800");
        assertThat(promo(PromoCodeType.FIXED_UZS, "5000").discountFor(bd("23800"))).isEqualByComparingTo("5000");
        assertThat(fixed.discountFor(BigDecimal.ZERO)).isEqualByComparingTo("0");
    }

    @Test
    void onlyActiveInsideItsPeriod() {
        PromoCode promo = promo(PromoCodeType.FIXED_UZS, "5000");
        assertThat(promo.refusal(NOW, 0)).isNull();

        promo.setValidFrom(NOW.plusSeconds(1));
        assertThat(promo.refusal(NOW, 0)).isEqualTo(PromoCode.Refusal.NOT_ACTIVE);
        promo.setValidFrom(NOW);
        promo.setValidTo(NOW);
        assertThat(promo.refusal(NOW, 0)).isEqualTo(PromoCode.Refusal.NOT_ACTIVE);
        promo.setValidTo(NOW.plusSeconds(60));
        assertThat(promo.refusal(NOW, 0)).isNull();
        promo.setActive(false);
        assertThat(promo.refusal(NOW, 0)).isEqualTo(PromoCode.Refusal.NOT_ACTIVE);
    }

    @Test
    void usageLimits() {
        PromoCode promo = promo(PromoCodeType.FIXED_UZS, "5000");
        promo.setMaxUses(2);
        promo.setPerUserLimit(1);

        assertThat(promo.refusal(NOW, 0)).isNull();
        assertThat(promo.refusal(NOW, 1)).isEqualTo(PromoCode.Refusal.USER_LIMIT_REACHED);
        promo.setUsedCount(2);
        assertThat(promo.refusal(NOW, 0)).isEqualTo(PromoCode.Refusal.LIMIT_REACHED);
        promo.setMaxUses(null);
        promo.setPerUserLimit(null);
        assertThat(promo.refusal(NOW, 100)).isNull();
    }

    private static PromoCode promo(PromoCodeType type, String value) {
        PromoCode promo = new PromoCode();
        promo.setCode("TEST");
        promo.setType(type);
        promo.setValue(bd(value));
        promo.setActive(true);
        return promo;
    }

    private static BigDecimal bd(String value) {
        return new BigDecimal(value);
    }
}
