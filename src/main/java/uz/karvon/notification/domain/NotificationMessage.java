package uz.karvon.notification.domain;

import java.util.List;

/**
 * What to tell the customer: a template key from messages_uz/messages_ru with its {0}, {1}... values.
 * Important messages go by SMS even when Telegram is linked.
 */
public record NotificationMessage(String templateKey, List<String> params, boolean important) {

    public NotificationMessage {
        params = List.copyOf(params);
    }
}
