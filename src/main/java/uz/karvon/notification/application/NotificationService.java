package uz.karvon.notification.application;

import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import uz.karvon.common.api.PageResponse;
import uz.karvon.common.application.BusinessException;
import uz.karvon.common.application.NotFoundException;
import uz.karvon.notification.api.dto.NotificationResponse;
import uz.karvon.notification.api.dto.NotificationSettingsResponse;
import uz.karvon.notification.domain.ChannelRule;
import uz.karvon.notification.domain.Notification;
import uz.karvon.notification.domain.NotificationChannel;
import uz.karvon.notification.domain.NotificationMessage;
import uz.karvon.notification.domain.NotificationStatus;
import uz.karvon.notification.infrastructure.NotificationRepository;
import uz.karvon.order.application.OrderNotificationFacts;
import uz.karvon.order.application.OrderNotificationQuery;
import uz.karvon.order.application.OrderRefundRequestedEvent;
import uz.karvon.order.application.OrderStatusChangedEvent;
import uz.karvon.payment.application.PaymentCompletedEvent;
import uz.karvon.user.application.NotificationContact;
import uz.karvon.user.application.UserService;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * Customer notifications: turns events into outbox rows (one per channel), lists them for staff and keeps the
 * customer's notification choices. Sending is done by {@link NotificationDispatcher}.
 * <p>
 * The enqueue methods run after the business transaction committed, in a new transaction of their own.
 */
@Service
@RequiredArgsConstructor
public class NotificationService {

    private static final Logger log = LoggerFactory.getLogger(NotificationService.class);

    private final NotificationRepository notifications;
    private final NotificationPlanner planner;
    private final NotificationParams params;
    private final NotificationTemplates templates;
    private final NotificationDispatcher dispatcher;
    private final UserService users;
    private final OrderNotificationQuery orders;

    /** Returns the ids of the saved rows (none if the event tells the customer nothing). */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public List<UUID> enqueue(OrderStatusChangedEvent event) {
        return planner.forStatusChange(event, () -> orders.facts(event.orderId())
                        .orElseGet(() -> OrderNotificationFacts.unknown(event.number())))
                .map(message -> save(event.customerId(), event.orderId(), message))
                .orElse(List.of());
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public List<UUID> enqueue(PaymentCompletedEvent event) {
        return planner.forPayment(event)
                .map(message -> save(event.customerId(), event.orderId(), message))
                .orElse(List.of());
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public List<UUID> enqueue(OrderRefundRequestedEvent event) {
        return planner.forRefund(event)
                .map(message -> save(event.customerId(), event.orderId(), message))
                .orElse(List.of());
    }

    /** ADMIN: newest first, optionally only one status. */
    @Transactional(readOnly = true)
    public PageResponse<NotificationResponse> search(NotificationStatus status, Pageable pageable) {
        return PageResponse.of(status == null ? notifications.findAll(pageable)
                : notifications.findByStatus(status, pageable), this::toResponse);
    }

    /**
     * ADMIN: tries a not yet sent notification again right now, even after the automatic retries ran out.
     * Returns it with the result of the try.
     */
    public NotificationResponse retry(UUID id) {
        Notification notification = load(id);
        if (notification.getStatus() == NotificationStatus.SENT) {
            throw new BusinessException(HttpStatus.CONFLICT, "NOTIFICATION_ALREADY_SENT", "error.notification.alreadySent");
        }
        dispatcher.send(id);
        return toResponse(load(id));
    }

    /** The retry job: ids to try now, oldest first. */
    @Transactional(readOnly = true)
    public List<UUID> due(Instant now, Instant pendingStuckBefore, int limit) {
        return notifications.findDue(NotificationStatus.FAILED, NotificationStatus.PENDING, now, pendingStuckBefore,
                PageRequest.of(0, limit));
    }

    public NotificationSettingsResponse settings(UUID userId) {
        return settings(users.notificationContact(userId)
                .orElseThrow(() -> new NotFoundException("USER_NOT_FOUND", "error.user.notFound")));
    }

    public NotificationSettingsResponse updateSettings(UUID userId, Boolean sms, Boolean telegram) {
        return settings(users.updateNotificationSettings(userId, sms, telegram));
    }

    private List<UUID> save(UUID userId, UUID orderId, NotificationMessage message) {
        Optional<NotificationContact> found = users.notificationContact(userId);
        if (found.isEmpty()) {
            log.warn("No user {} for notification {}", userId, message.templateKey());
            return List.of();
        }
        NotificationContact contact = found.get();
        Set<NotificationChannel> channels = ChannelRule.channels(contact.telegramLinked(), contact.telegramEnabled(),
                contact.smsEnabled(), message.important());
        String values = params.write(message.params());
        List<UUID> ids = new ArrayList<>();
        for (NotificationChannel channel : channels) {
            String recipient = channel == NotificationChannel.SMS ? contact.phone() : contact.telegramChatId().toString();
            ids.add(notifications.save(new Notification(userId, orderId, channel, recipient, contact.language(),
                    message.templateKey(), values)).getId());
        }
        return ids;
    }

    private Notification load(UUID id) {
        return notifications.findById(id)
                .orElseThrow(() -> new NotFoundException("NOTIFICATION_NOT_FOUND", "error.notification.notFound"));
    }

    private NotificationResponse toResponse(Notification n) {
        List<String> values = params.read(n.getParams());
        return new NotificationResponse(n.getId(), n.getUserId(), n.getOrderId(), n.getChannel(), n.getRecipient(),
                n.getLanguage(), n.getTemplateKey(), values, templates.render(n.getTemplateKey(), values, n.getLanguage()),
                n.getStatus(), n.getAttempts(), n.getLastError(), n.getNextAttemptAt(), n.getSentAt(), n.getCreatedAt());
    }

    private static NotificationSettingsResponse settings(NotificationContact contact) {
        return new NotificationSettingsResponse(contact.smsEnabled(), contact.telegramEnabled(), contact.telegramLinked());
    }
}
