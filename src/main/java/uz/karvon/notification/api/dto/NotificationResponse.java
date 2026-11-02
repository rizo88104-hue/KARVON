package uz.karvon.notification.api.dto;

import uz.karvon.notification.domain.NotificationChannel;
import uz.karvon.notification.domain.NotificationStatus;
import uz.karvon.user.domain.Language;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * A notification as staff see it, with the rendered text. {@code nextAttemptAt} is when the retry job tries again
 * (null: no automatic retry).
 */
public record NotificationResponse(
        UUID id,
        UUID userId,
        UUID orderId,
        NotificationChannel channel,
        String recipient,
        Language language,
        String templateKey,
        List<String> params,
        String text,
        NotificationStatus status,
        int attempts,
        String lastError,
        Instant nextAttemptAt,
        Instant sentAt,
        Instant createdAt) {
}
