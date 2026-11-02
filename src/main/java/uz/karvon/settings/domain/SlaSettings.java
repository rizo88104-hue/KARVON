package uz.karvon.settings.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import org.hibernate.annotations.Immutable;

import java.time.Instant;
import java.util.UUID;

/**
 * One version of the SLA limits: order status name -> maximum hours, stored as JSON. Rows are only inserted;
 * the newest is active.
 */
@Getter
@Entity
@Immutable
@Table(name = "sla_settings")
public class SlaSettings {

    @Id
    private UUID id;

    @Column(name = "limits", nullable = false)
    private String limits;

    @Column(name = "effective_from", nullable = false)
    private Instant effectiveFrom;

    @Column(name = "changed_by")
    private UUID changedBy;

    protected SlaSettings() {
    }

    public SlaSettings(UUID id, String limits, Instant effectiveFrom, UUID changedBy) {
        this.id = id;
        this.limits = limits;
        this.effectiveFrom = effectiveFrom;
        this.changedBy = changedBy;
    }
}
