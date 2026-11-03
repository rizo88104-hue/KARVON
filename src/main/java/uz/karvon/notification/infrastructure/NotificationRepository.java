package uz.karvon.notification.infrastructure;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import uz.karvon.notification.domain.Notification;
import uz.karvon.notification.domain.NotificationChannel;
import uz.karvon.notification.domain.NotificationStatus;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface NotificationRepository extends JpaRepository<Notification, UUID> {

    Page<Notification> findByStatus(NotificationStatus status, Pageable pageable);

    List<Notification> findByUserIdOrderByCreatedAtAsc(UUID userId);

    Page<Notification> findByUserId(UUID userId, Pageable pageable);

    Page<Notification> findByUserIdAndChannel(UUID userId, NotificationChannel channel, Pageable pageable);

    long countByUserIdAndChannelAndReadAtIsNull(UUID userId, NotificationChannel channel);

    Optional<Notification> findByIdAndUserIdAndChannel(UUID id, UUID userId, NotificationChannel channel);

    /** Marks every unread in-app notification of the user read; returns how many. */
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("""
            update Notification n set n.readAt = :now, n.updatedAt = :now
            where n.userId = :userId and n.channel = :channel and n.readAt is null""")
    int markAllRead(@Param("userId") UUID userId, @Param("channel") NotificationChannel channel,
                    @Param("now") Instant now);

    /**
     * Takes the notification for one send: counts the attempt and moves {@code nextAttemptAt} to {@code lease},
     * so nobody else sends it meanwhile. Returns 0 if it was sent already or someone else holds it.
     */
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("""
            update Notification n set n.attempts = n.attempts + 1, n.nextAttemptAt = :lease, n.updatedAt = :now
            where n.id = :id and n.status <> :sent and n.nextAttemptAt <= :now""")
    int claim(@Param("id") UUID id, @Param("now") Instant now, @Param("lease") Instant lease,
              @Param("sent") NotificationStatus sent);

    /** Not sent, due now and with retries left (attempts counts the first send too). */
    @Query("""
            select n.id from Notification n
            where n.status in :statuses and n.nextAttemptAt <= :now and n.attempts <= :maxRetries
            order by n.nextAttemptAt""")
    List<UUID> findDue(@Param("statuses") Collection<NotificationStatus> statuses, @Param("now") Instant now,
                       @Param("maxRetries") int maxRetries, Pageable pageable);
}
