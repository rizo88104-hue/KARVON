package uz.karvon.order.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import org.hibernate.annotations.Immutable;

import java.time.Instant;
import java.util.UUID;

/**
 * One status change. Written for every change, never edited. {@code fromStatus} is null for "order created".
 */
@Getter
@Entity
@Immutable
@Table(name = "order_status_history")
public class OrderStatusHistoryEntry {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "order_id", nullable = false)
    private UUID orderId;

    @Enumerated(EnumType.STRING)
    @Column(name = "from_status", length = 30)
    private OrderStatus fromStatus;

    @Enumerated(EnumType.STRING)
    @Column(name = "to_status", nullable = false, length = 30)
    private OrderStatus toStatus;

    /** Null when the system made the change. */
    @Column(name = "changed_by_user_id")
    private UUID changedByUserId;

    @Enumerated(EnumType.STRING)
    @Column(name = "changed_by_role", nullable = false, length = 20)
    private Actor changedByRole;

    @Column(name = "comment", length = 500)
    private String comment;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    protected OrderStatusHistoryEntry() {
    }

    public OrderStatusHistoryEntry(UUID orderId, OrderStatus fromStatus, OrderStatus toStatus, UUID changedByUserId,
                                   Actor changedByRole, String comment, Instant createdAt) {
        this.orderId = orderId;
        this.fromStatus = fromStatus;
        this.toStatus = toStatus;
        this.changedByUserId = changedByUserId;
        this.changedByRole = changedByRole;
        this.comment = comment;
        this.createdAt = createdAt;
    }
}
