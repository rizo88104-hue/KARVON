package uz.karvon.pricing.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import org.hibernate.annotations.Immutable;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * One order that used a promo code.
 */
@Getter
@Entity
@Immutable
@Table(name = "promo_code_usages")
public class PromoCodeUsage {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "promo_code_id", nullable = false, updatable = false)
    private UUID promoCodeId;

    @Column(name = "user_id", nullable = false, updatable = false)
    private UUID userId;

    @Column(name = "order_id", nullable = false, updatable = false, unique = true)
    private UUID orderId;

    @Column(name = "discount_uzs", nullable = false, updatable = false)
    private BigDecimal discountUzs;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected PromoCodeUsage() {
    }

    public PromoCodeUsage(UUID promoCodeId, UUID userId, UUID orderId, BigDecimal discountUzs, Instant createdAt) {
        this.promoCodeId = promoCodeId;
        this.userId = userId;
        this.orderId = orderId;
        this.discountUzs = discountUzs;
        this.createdAt = createdAt;
    }
}
