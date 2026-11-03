package uz.karvon.notification.domain;

/**
 * IN_APP: shown on the site (bell in the header, GET /api/v1/me/notifications), used when nothing else can reach the
 * user. It is saved as SENT and never goes to a provider.
 */
public enum NotificationChannel {
    SMS, TELEGRAM, IN_APP
}
