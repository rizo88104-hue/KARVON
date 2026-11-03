package uz.karvon.notification.api.dto;

/**
 * Channels the customer allows, whether a Telegram chat is linked (GET /api/v1/me/telegram-link to link one), and
 * whether the shop sends SMS at all ({@code smsAvailable}, karvon.sms.enabled; hide the SMS switch when false).
 */
public record NotificationSettingsResponse(boolean sms, boolean telegram, boolean telegramLinked, boolean smsAvailable) {
}
