package uz.karvon.auth.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * Where the login code goes. TELEGRAM: sent to the linked Telegram chat. SMS: sent by SMS (only while SMS is
 * turned on). TELEGRAM_LINK: no chat is linked yet; open {@code link}, press Start and share the phone number in the
 * bot, which then sends the code. {@code expiresInSeconds}: how long the code (or the link) is valid.
 */
public record OtpRequestResponse(
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) OtpChannel channel,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) long expiresInSeconds,
        @Schema(description = "Only for TELEGRAM_LINK", example = "https://t.me/emarket_bot?start=AbC123")
        String link) {

    public enum OtpChannel {
        TELEGRAM, TELEGRAM_LINK, SMS
    }
}
