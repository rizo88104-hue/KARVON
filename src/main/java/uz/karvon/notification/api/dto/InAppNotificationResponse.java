package uz.karvon.notification.api.dto;

import java.time.Instant;
import java.util.UUID;

/**
 * A notification shown on the site (the bell), its text in the site language. {@code orderId} is set for order
 * messages.
 */
public record InAppNotificationResponse(UUID id, UUID orderId, String templateKey, String text, boolean read,
                                        Instant createdAt) {
}
