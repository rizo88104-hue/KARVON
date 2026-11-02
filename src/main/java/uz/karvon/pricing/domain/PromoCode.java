package uz.karvon.pricing.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;
import uz.karvon.common.domain.BaseEntity;
import uz.karvon.common.domain.Money;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Objects;

/**
 * A promo code that lowers the service fee of an order. The fee never goes below 0.
 * {@code maxUses} / {@code perUserLimit} null mean no limit; {@code validTo} is exclusive.
 */
@Getter
@Setter
@Entity
@Table(name = "promo_codes")
public class PromoCode extends BaseEntity {

    /** Upper case. */
    @Column(name = "code", nullable = false, unique = true, length = 40)
    private String code;

    @Enumerated(EnumType.STRING)
    @Column(name = "type", nullable = false, length = 20)
    private PromoCodeType type;

    @Column(name = "value", nullable = false)
    private BigDecimal value;

    @Column(name = "max_uses")
    private Integer maxUses;

    @Column(name = "per_user_limit")
    private Integer perUserLimit;

    @Column(name = "valid_from")
    private Instant validFrom;

    @Column(name = "valid_to")
    private Instant validTo;

    @Column(name = "active", nullable = false)
    private boolean active;

    /** Orders that use it now (an unpaid order cancelled gives its use back). */
    @Column(name = "used_count", nullable = false)
    private int usedCount;

    /** Why it cannot be used now, or null when it can. */
    public Refusal refusal(Instant now, long usesByUser) {
        if (!active || (validFrom != null && now.isBefore(validFrom)) || (validTo != null && !now.isBefore(validTo))) {
            return Refusal.NOT_ACTIVE;
        }
        if (maxUses != null && usedCount >= maxUses) {
            return Refusal.LIMIT_REACHED;
        }
        if (perUserLimit != null && usesByUser >= perUserLimit) {
            return Refusal.USER_LIMIT_REACHED;
        }
        return null;
    }

    /**
     * How much of {@code serviceFeeUzs} this code takes off, rounded to 100 so'm (HALF_UP) and never more than the fee.
     */
    public BigDecimal discountFor(BigDecimal serviceFeeUzs) {
        Objects.requireNonNull(serviceFeeUzs, "serviceFeeUzs");
        if (serviceFeeUzs.signum() <= 0) {
            return BigDecimal.ZERO;
        }
        BigDecimal discount = switch (type) {
            case PERCENT_OF_FEE -> serviceFeeUzs.multiply(value).movePointLeft(2);
            case FIXED_UZS -> value;
        };
        return Money.roundUzs(discount).min(serviceFeeUzs);
    }

    public enum Refusal {
        NOT_ACTIVE, LIMIT_REACHED, USER_LIMIT_REACHED
    }
}
