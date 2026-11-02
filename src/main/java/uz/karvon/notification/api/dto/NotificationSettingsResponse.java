package uz.karvon.notification.api.dto;

/**
 * Channels the customer allows, and whether a Telegram chat is linked (GET /api/v1/me/telegram-link to link one).
 */
public record NotificationSettingsResponse(boolean sms, boolean telegram, boolean telegramLinked) {
}
