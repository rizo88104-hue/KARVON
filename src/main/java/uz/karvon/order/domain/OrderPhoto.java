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
 * A parcel photo taken at the warehouse. The file is in FileStorage under {@code storageKey}.
 */
@Getter
@Entity
@Immutable
@Table(name = "order_photos")
public class OrderPhoto {

    public static final int MAX_PER_ORDER = 4;

    @Id
    private UUID id;

    @Column(name = "order_id", nullable = false)
    private UUID orderId;

    @Column(name = "storage_key", nullable = false, length = 300)
    private String storageKey;

    @Column(name = "content_type", nullable = false, length = 50)
    private String contentType;

    @Column(name = "size_bytes", nullable = false)
    private long sizeBytes;

    @Column(name = "uploaded_by", nullable = false)
    private UUID uploadedBy;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    protected OrderPhoto() {
    }

    public OrderPhoto(UUID id, UUID orderId, String storageKey, String contentType, long sizeBytes, UUID uploadedBy,
                      Instant createdAt) {
        this.id = id;
        this.orderId = orderId;
        this.storageKey = storageKey;
        this.contentType = contentType;
        this.sizeBytes = sizeBytes;
        this.uploadedBy = uploadedBy;
        this.createdAt = createdAt;
    }
}
