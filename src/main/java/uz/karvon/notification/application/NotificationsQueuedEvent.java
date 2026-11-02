package uz.karvon.notification.application;

import java.util.List;
import java.util.UUID;

/**
 * Notifications were written (or put back to PENDING by an admin); they are sent once that transaction commits.
 */
public record NotificationsQueuedEvent(List<UUID> notificationIds) {
}
