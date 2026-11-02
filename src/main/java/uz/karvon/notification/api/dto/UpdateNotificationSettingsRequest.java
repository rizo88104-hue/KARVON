package uz.karvon.notification.api.dto;

/**
 * Partial update: a null field is left unchanged.
 */
public record UpdateNotificationSettingsRequest(Boolean sms, Boolean telegram) {
}
