package uz.karvon.notification.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import lombok.Getter;
import uz.karvon.common.domain.BaseEntity;

import java.time.Instant;
import java.util.UUID;

/**
 * One message to one user over one channel (outbox row). The text is rendered when it is sent,
 * from {@code templateKey} and {@code params} (JSON array), in the user's language.
 * {@code attempts} counts every send tried, the first one included.
 */
@Getter
@Entity
@Table(name = "notifications")
public class Notification extends BaseEntity {

    static final int MAX_ERROR_LENGTH = 500;

    @Column(name = "user_id", nullable = false, updatable = false)
    private UUID userId;

    @Column(name = "order_id", updatable = false)
    private UUID orderId;

    @Enumerated(EnumType.STRING)
    @Column(name = "channel", nullable = false, updatable = false, length = 10)
    private NotificationChannel channel;

    @Column(name = "template_key", nullable = false, updatable = false, length = 60)
    private String templateKey;

    @Column(name = "params", nullable = false, updatable = false)
    private String params;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 10)
    private NotificationStatus status;

    @Column(name = "attempts", nullable = false)
    private int attempts;

    @Column(name = "last_error", length = MAX_ERROR_LENGTH)
    private String lastError;

    @Column(name = "sent_at")
    private Instant sentAt;

    @Column(name = "next_attempt_at", nullable = false)
    private Instant nextAttemptAt;

    protected Notification() {
    }

    public Notification(UUID userId, UUID orderId, NotificationChannel channel, String templateKey, String paramsJson,
                        Instant now) {
        this.userId = userId;
        this.orderId = orderId;
        this.channel = channel;
        this.templateKey = templateKey;
        this.params = paramsJson;
        this.status = NotificationStatus.PENDING;
        this.nextAttemptAt = now;
    }

    public void sent(Instant now) {
        status = NotificationStatus.SENT;
        sentAt = now;
        lastError = null;
    }

    /** The last counted attempt failed; the next one may happen after the backoff delay. */
    public void failed(String error, Instant now, RetryBackoff backoff) {
        status = NotificationStatus.FAILED;
        String text = error == null || error.isBlank() ? "unknown error" : error;
        lastError = text.length() > MAX_ERROR_LENGTH ? text.substring(0, MAX_ERROR_LENGTH) : text;
        nextAttemptAt = now.plus(backoff.delayAfter(attempts));
    }

    /** ADMIN: send again now, even after the automatic retries ran out. */
    public void retryNow(Instant now) {
        status = NotificationStatus.PENDING;
        nextAttemptAt = now;
    }
}
