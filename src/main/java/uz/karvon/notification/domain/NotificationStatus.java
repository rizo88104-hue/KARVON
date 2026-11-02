package uz.karvon.notification.domain;

/**
 * PENDING: waiting to be sent (first time or after an admin retry); SENT; FAILED: the last attempt failed.
 */
public enum NotificationStatus {
    PENDING, SENT, FAILED
}
