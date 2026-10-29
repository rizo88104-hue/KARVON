package uz.karvon.order.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import org.hibernate.annotations.Immutable;

import java.time.Instant;
import java.util.UUID;

/**
 * An internal staff note on an order. Never shown to the customer.
 */
@Getter
@Entity
@Immutable
@Table(name = "order_comments")
public class OrderComment {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "order_id", nullable = false)
    private UUID orderId;

    @Column(name = "author_id", nullable = false)
    private UUID authorId;

    @Column(name = "text", nullable = false, length = 2000)
    private String text;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    protected OrderComment() {
    }

    public OrderComment(UUID orderId, UUID authorId, String text, Instant createdAt) {
        this.orderId = orderId;
        this.authorId = authorId;
        this.text = text;
        this.createdAt = createdAt;
    }
}
