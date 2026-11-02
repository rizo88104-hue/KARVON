package uz.karvon.notification.api.dto;

import uz.karvon.notification.domain.NotificationChannel;
import uz.karvon.notification.domain.NotificationStatus;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * A notification for staff. {@code attempts} counts every send tried, the first one included.
 */
public record NotificationResponse(UUID id, UUID userId, UUID orderId, NotificationChannel channel, String templateKey,
                                   List<String> params, NotificationStatus status, int attempts, String lastError,
                                   Instant sentAt, Instant nextAttemptAt, Instant createdAt) {
}
