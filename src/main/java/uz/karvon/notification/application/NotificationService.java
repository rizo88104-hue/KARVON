package uz.karvon.notification.application;

import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.json.JsonMapper;
import uz.karvon.common.api.PageResponse;
import uz.karvon.common.application.BusinessException;
import uz.karvon.common.application.NotFoundException;
import uz.karvon.notification.api.dto.NotificationResponse;
import uz.karvon.notification.api.dto.NotificationSettingsResponse;
import uz.karvon.notification.api.dto.UpdateNotificationSettingsRequest;
import uz.karvon.notification.domain.ChannelRule;
import uz.karvon.notification.domain.Notification;
import uz.karvon.notification.domain.NotificationChannel;
import uz.karvon.notification.domain.NotificationMessage;
import uz.karvon.notification.domain.NotificationPreferences;
import uz.karvon.notification.domain.NotificationSettings;
import uz.karvon.notification.domain.NotificationStatus;
import uz.karvon.notification.domain.RetryBackoff;
import uz.karvon.notification.infrastructure.NotificationProperties;
import uz.karvon.notification.infrastructure.NotificationRepository;
import uz.karvon.notification.infrastructure.NotificationSettingsRepository;
import uz.karvon.user.application.UserContact;
import uz.karvon.user.application.UserService;

import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * The notification outbox: writes rows for a message (one per channel), hands them out for sending one at a time,
 * records the result, and keeps the user's channel settings.
 */
@Service
@Transactional(readOnly = true)
public class NotificationService {

    private static final TypeReference<List<String>> PARAMS = new TypeReference<>() {
    };

    private final NotificationRepository notifications;
    private final NotificationSettingsRepository settings;
    private final UserService users;
    private final ApplicationEventPublisher events;
    private final JsonMapper json;
    private final Clock clock;
    private final NotificationProperties properties;
    private final RetryBackoff backoff;

    public NotificationService(NotificationRepository notifications, NotificationSettingsRepository settings,
                               UserService users, ApplicationEventPublisher events, JsonMapper json, Clock clock,
                               NotificationProperties properties) {
        this.notifications = notifications;
        this.settings = settings;
        this.users = users;
        this.events = events;
        this.json = json;
        this.clock = clock;
        this.properties = properties;
        this.backoff = properties.backoff();
    }

    /** A notification taken for one send attempt. */
    public record Outgoing(UUID id, UUID userId, NotificationChannel channel, String templateKey, List<String> params) {
    }

    /**
     * Writes PENDING rows for the channels chosen by {@link ChannelRule}; they are sent after this commits.
     * Runs in its own transaction: it is called after the business transaction has committed.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public List<UUID> enqueue(NotificationMessage message) {
        Optional<UserContact> contact = users.contact(message.userId());
        if (contact.isEmpty()) {
            return List.of();
        }
        Set<NotificationChannel> channels = ChannelRule.channels(message.important(), contact.get().telegramLinked(),
                preferences(message.userId()));
        Instant now = Instant.now(clock);
        String params = json.writeValueAsString(message.params());
        List<UUID> ids = new ArrayList<>();
        for (NotificationChannel channel : channels) {
            ids.add(notifications.save(new Notification(message.userId(), message.orderId(), channel,
                    message.templateKey(), params, now)).getId());
        }
        if (!ids.isEmpty()) {
            events.publishEvent(new NotificationsQueuedEvent(ids));
        }
        return ids;
    }

    /** Counts an attempt and returns the notification, or empty if it was sent already or is being sent. */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public Optional<Outgoing> claim(UUID id) {
        Instant now = Instant.now(clock);
        if (notifications.claim(id, now, now.plus(properties.sendingLease()), NotificationStatus.SENT) == 0) {
            return Optional.empty();
        }
        return notifications.findById(id).map(n -> new Outgoing(n.getId(), n.getUserId(), n.getChannel(),
                n.getTemplateKey(), json.readValue(n.getParams(), PARAMS)));
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void markSent(UUID id) {
        notifications.findById(id).ifPresent(n -> n.sent(Instant.now(clock)));
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void markFailed(UUID id, String error) {
        notifications.findById(id).ifPresent(n -> n.failed(error, Instant.now(clock), backoff));
    }

    /** What the retry job sends now: PENDING or FAILED, due, with retries left. */
    public List<UUID> due() {
        return notifications.findDue(EnumSet.of(NotificationStatus.PENDING, NotificationStatus.FAILED),
                Instant.now(clock), properties.maxRetries(), PageRequest.of(0, properties.batchSize()));
    }

    // --- customer settings

    public NotificationSettingsResponse settings(UUID userId) {
        return settingsResponse(userId, preferences(userId));
    }

    /** Partial update: a null field is left unchanged. */
    @Transactional
    public NotificationSettingsResponse updateSettings(UUID userId, UpdateNotificationSettingsRequest request) {
        NotificationPreferences current = preferences(userId);
        NotificationPreferences updated = new NotificationPreferences(
                request.sms() == null ? current.sms() : request.sms(),
                request.telegram() == null ? current.telegram() : request.telegram());
        NotificationSettings row = settings.findById(userId)
                .orElseGet(() -> new NotificationSettings(userId, updated, Instant.now(clock)));
        row.setSms(updated.sms());
        row.setTelegram(updated.telegram());
        row.setUpdatedAt(Instant.now(clock));
        settings.save(row);
        return settingsResponse(userId, updated);
    }

    // --- admin

    /** All notifications or those with {@code status}, newest first. */
    public PageResponse<NotificationResponse> search(NotificationStatus status, Pageable pageable) {
        var page = status == null ? notifications.findAll(pageable) : notifications.findByStatus(status, pageable);
        return PageResponse.of(page, this::response);
    }

    /** Notifications of one user, as {@code pageable} sorts them (customer support). */
    public PageResponse<NotificationResponse> forUser(UUID userId, Pageable pageable) {
        return PageResponse.of(notifications.findByUserId(userId, pageable), this::response);
    }

    /** ADMIN: send a notification again now. A SENT one is refused. */
    @Transactional
    public NotificationResponse retry(UUID id) {
        Notification notification = notifications.findById(id)
                .orElseThrow(() -> new NotFoundException("NOTIFICATION_NOT_FOUND", "error.notification.notFound"));
        if (notification.getStatus() == NotificationStatus.SENT) {
            throw new BusinessException(HttpStatus.CONFLICT, "NOTIFICATION_ALREADY_SENT",
                    "error.notification.alreadySent");
        }
        notification.retryNow(Instant.now(clock));
        events.publishEvent(new NotificationsQueuedEvent(List.of(id)));
        return response(notification);
    }

    private NotificationPreferences preferences(UUID userId) {
        return settings.findById(userId).map(NotificationSettings::preferences).orElse(NotificationPreferences.DEFAULT);
    }

    private NotificationSettingsResponse settingsResponse(UUID userId, NotificationPreferences preferences) {
        boolean linked = users.contact(userId).map(UserContact::telegramLinked).orElse(false);
        return new NotificationSettingsResponse(preferences.sms(), preferences.telegram(), linked);
    }

    private NotificationResponse response(Notification n) {
        return new NotificationResponse(n.getId(), n.getUserId(), n.getOrderId(), n.getChannel(), n.getTemplateKey(),
                json.readValue(n.getParams(), PARAMS), n.getStatus(), n.getAttempts(), n.getLastError(), n.getSentAt(),
                n.getNextAttemptAt(), n.getCreatedAt());
    }
}
