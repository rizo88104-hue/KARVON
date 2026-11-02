package uz.karvon.notification.application;

import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import uz.karvon.notification.infrastructure.NotificationConfig;

import java.util.List;
import java.util.UUID;

/**
 * Sends notifications on the bounded notification pool, outside the caller's thread.
 */
@Component
@RequiredArgsConstructor
public class NotificationDispatcher {

    private final NotificationDelivery delivery;

    @Async(NotificationConfig.EXECUTOR)
    public void dispatch(List<UUID> notificationIds) {
        notificationIds.forEach(delivery::deliver);
    }
}
