package uz.karvon.notification.domain;

/**
 * Channels the user allows. Both are allowed by default.
 */
public record NotificationPreferences(boolean sms, boolean telegram) {

    public static final NotificationPreferences DEFAULT = new NotificationPreferences(true, true);
}
