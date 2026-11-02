package uz.karvon.notification.domain;

import java.util.List;
import java.util.UUID;

/**
 * What to tell a user, before choosing channels. {@code important} messages also go by SMS when Telegram is linked.
 */
public record NotificationMessage(UUID userId, UUID orderId, String templateKey, List<String> params,
                                  boolean important) {

    public NotificationMessage {
        params = List.copyOf(params);
    }
}
