package uz.karvon.settings.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Builder;
import lombok.Getter;
import org.hibernate.annotations.Immutable;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * One version of the pricing settings. Rows are never updated: a change inserts a new row,
 * and the row with the newest {@code effectiveFrom} is active.
 */
@Getter
@Entity
@Immutable
@Table(name = "pricing_settings")
public class PricingSettings {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    /** UZS for 1 CNY. */
    @Column(name = "customer_rate", nullable = false, updatable = false)
    private BigDecimal customerRate;

    /** Percent of the goods price: 10 means 10%. */
    @Column(name = "fee_percent", nullable = false, updatable = false)
    private BigDecimal feePercent;

    @Column(name = "min_service_fee", nullable = false, updatable = false)
    private BigDecimal minServiceFee;

    @Column(name = "shipping_cost_per_kg", nullable = false, updatable = false)
    private BigDecimal shippingCostPerKg;

    @Column(name = "our_markup_per_kg", nullable = false, updatable = false)
    private BigDecimal ourMarkupPerKg;

    @Column(name = "min_weight_kg", nullable = false, updatable = false)
    private BigDecimal minWeightKg;

    @Column(name = "default_weight_kg", nullable = false, updatable = false)
    private BigDecimal defaultWeightKg;

    @Column(name = "price_change_threshold_percent", nullable = false, updatable = false)
    private BigDecimal priceChangeThresholdPercent;

    @Column(name = "effective_from", nullable = false, updatable = false)
    private Instant effectiveFrom;

    /** User id of the ADMIN who made the change; null for the seed row. */
    @Column(name = "changed_by", updatable = false)
    private UUID changedBy;

    protected PricingSettings() {
    }

    @Builder
    private PricingSettings(BigDecimal customerRate, BigDecimal feePercent, BigDecimal minServiceFee,
                            BigDecimal shippingCostPerKg, BigDecimal ourMarkupPerKg, BigDecimal minWeightKg,
                            BigDecimal defaultWeightKg, BigDecimal priceChangeThresholdPercent,
                            Instant effectiveFrom, UUID changedBy) {
        this.customerRate = customerRate;
        this.feePercent = feePercent;
        this.minServiceFee = minServiceFee;
        this.shippingCostPerKg = shippingCostPerKg;
        this.ourMarkupPerKg = ourMarkupPerKg;
        this.minWeightKg = minWeightKg;
        this.defaultWeightKg = defaultWeightKg;
        this.priceChangeThresholdPercent = priceChangeThresholdPercent;
        this.effectiveFrom = effectiveFrom;
        this.changedBy = changedBy;
    }
}
