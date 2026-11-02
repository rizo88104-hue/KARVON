package uz.karvon.notification.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import lombok.Getter;
import uz.karvon.common.domain.BaseEntity;
import uz.karvon.user.domain.Language;

import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

/**
 * One message to one channel (outbox row). Created PENDING; each try ends SENT or FAILED.
 * A FAILED row is retried up to {@link #MAX_RETRIES} times, waiting 1, 2, 4, 8 and 16 minutes;
 * after that {@code nextAttemptAt} is null and only an admin can retry it.
 */
@Getter
@Entity
@Table(name = "notifications")
public class Notification extends BaseEntity {

    public static final int MAX_RETRIES = 5;
    static final Duration FIRST_RETRY_DELAY = Duration.ofMinutes(1);
    private static final int MAX_ERROR_LENGTH = 500;

    @Column(name = "user_id", nullable = false, updatable = false)
    private UUID userId;

    @Column(name = "order_id", updatable = false)
    private UUID orderId;

    @Enumerated(EnumType.STRING)
    @Column(name = "channel", nullable = false, updatable = false, length = 10)
    private NotificationChannel channel;

    /** Phone (E.164) for SMS, chat id for Telegram. */
    @Column(name = "recipient", nullable = false, updatable = false, length = 32)
    private String recipient;

    @Enumerated(EnumType.STRING)
    @Column(name = "language", nullable = false, updatable = false, length = 2)
    private Language language;

    @Column(name = "template_key", nullable = false, updatable = false, length = 64)
    private String templateKey;

    /** JSON array of strings. */
    @Column(name = "params", nullable = false, updatable = false)
    private String params;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 10)
    private NotificationStatus status = NotificationStatus.PENDING;

    @Column(name = "attempts", nullable = false)
    private int attempts;

    @Column(name = "last_error", length = MAX_ERROR_LENGTH)
    private String lastError;

    @Column(name = "next_attempt_at")
    private Instant nextAttemptAt;

    @Column(name = "sent_at")
    private Instant sentAt;

    protected Notification() {
    }

    public Notification(UUID userId, UUID orderId, NotificationChannel channel, String recipient, Language language,
                        String templateKey, String params) {
        this.userId = userId;
        this.orderId = orderId;
        this.channel = channel;
        this.recipient = recipient;
        this.language = language;
        this.templateKey = templateKey;
        this.params = params;
    }

    public void markSent(Instant now) {
        attempts++;
        status = NotificationStatus.SENT;
        sentAt = now;
        lastError = null;
        nextAttemptAt = null;
    }

    public void markFailed(String error, Instant now) {
        attempts++;
        status = NotificationStatus.FAILED;
        lastError = error == null || error.length() <= MAX_ERROR_LENGTH ? error : error.substring(0, MAX_ERROR_LENGTH);
        // The first try is not a retry: attempts - 1 retries were made so far.
        nextAttemptAt = attempts - 1 < MAX_RETRIES ? now.plus(retryDelay(attempts)) : null;
    }

    /** Whether the retry job will try again. */
    public boolean willRetry() {
        return status == NotificationStatus.FAILED && nextAttemptAt != null;
    }

    /** Wait before the next try after {@code failedAttempts} failures: 1, 2, 4, 8, 16 minutes. */
    static Duration retryDelay(int failedAttempts) {
        return FIRST_RETRY_DELAY.multipliedBy(1L << Math.min(failedAttempts - 1, 10));
    }
}
