package uz.karvon.notification.api.dto;

import java.time.Instant;

/**
 * A one-time deep link to our bot (https://t.me/{bot}?start={token}). Opening it and pressing Start links the chat.
 */
public record TelegramLinkResponse(String url, Instant expiresAt) {
}
