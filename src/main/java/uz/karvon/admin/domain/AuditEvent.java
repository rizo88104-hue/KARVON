package uz.karvon.admin.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Builder;
import lombok.Getter;
import org.hibernate.annotations.Immutable;

import java.time.Instant;
import java.util.UUID;

/**
 * One staff write action. Rows are never changed.
 */
@Getter
@Entity
@Immutable
@Table(name = "audit_events")
public class AuditEvent {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "actor_id", nullable = false, updatable = false)
    private UUID actorId;

    @Column(name = "actor_role", nullable = false, updatable = false, length = 16)
    private String actorRole;

    @Column(name = "action", nullable = false, updatable = false, length = 60)
    private String action;

    @Column(name = "entity_type", nullable = false, updatable = false, length = 40)
    private String entityType;

    @Column(name = "entity_id", updatable = false, length = 64)
    private String entityId;

    @Column(name = "before_json", updatable = false)
    private String beforeJson;

    @Column(name = "after_json", updatable = false)
    private String afterJson;

    @Column(name = "ip", updatable = false, length = 64)
    private String ip;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected AuditEvent() {
    }

    @Builder
    private AuditEvent(UUID actorId, String actorRole, String action, String entityType, String entityId,
                       String beforeJson, String afterJson, String ip, Instant createdAt) {
        this.actorId = actorId;
        this.actorRole = actorRole;
        this.action = action;
        this.entityType = entityType;
        this.entityId = entityId;
        this.beforeJson = beforeJson;
        this.afterJson = afterJson;
        this.ip = ip;
        this.createdAt = createdAt;
    }
}
