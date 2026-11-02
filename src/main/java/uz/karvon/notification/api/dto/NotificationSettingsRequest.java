package uz.karvon.notification.api.dto;

/**
 * Turns SMS and Telegram notifications on or off. A missing (null) field is left as it is.
 * Login codes are always sent by SMS.
 */
public record NotificationSettingsRequest(Boolean sms, Boolean telegram) {
}
