package uz.karvon.notification.domain;

public enum NotificationStatus {
    /** Saved, not tried yet. */
    PENDING,
    SENT,
    /** The last try failed; retried while {@code nextAttemptAt} is set. */
    FAILED
}
