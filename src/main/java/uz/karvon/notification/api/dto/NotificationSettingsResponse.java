package uz.karvon.notification.api.dto;

/**
 * {@code telegramLinked}: the customer linked a Telegram chat with GET /api/v1/me/telegram-link.
 */
public record NotificationSettingsResponse(boolean sms, boolean telegram, boolean telegramLinked) {
}
