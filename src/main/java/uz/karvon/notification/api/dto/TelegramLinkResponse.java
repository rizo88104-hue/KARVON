package uz.karvon.notification.api.dto;

import java.time.Instant;

/**
 * One-time deep link to the bot; opening it and pressing Start links the chat. Works once, until {@code expiresAt}.
 */
public record TelegramLinkResponse(String url, Instant expiresAt) {
}
