package uz.karvon.notification.infrastructure;

import jakarta.persistence.LockModeType;
import jakarta.persistence.QueryHint;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.jpa.repository.QueryHints;
import org.springframework.data.repository.query.Param;
import uz.karvon.notification.domain.Notification;
import uz.karvon.notification.domain.NotificationStatus;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface NotificationRepository extends JpaRepository<Notification, UUID> {

    /**
     * Locks the row for one sender. SKIP LOCKED: empty if another thread or instance is sending it right now
     * (or the row does not exist), so a message is never sent twice in parallel.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @QueryHints(@QueryHint(name = "jakarta.persistence.lock.timeout", value = "-2"))
    @Query("select n from Notification n where n.id = :id")
    Optional<Notification> lockForSending(@Param("id") UUID id);

    /** FAILED rows whose retry time has come, and PENDING rows nobody picked up (e.g. after a restart). */
    @Query("""
            select n.id from Notification n
            where (n.status = :failed and n.nextAttemptAt <= :now)
               or (n.status = :pending and n.createdAt < :stuckBefore)
            order by n.createdAt""")
    List<UUID> findDue(@Param("failed") NotificationStatus failed, @Param("pending") NotificationStatus pending,
                       @Param("now") Instant now, @Param("stuckBefore") Instant stuckBefore, Pageable limit);

    Page<Notification> findByStatus(NotificationStatus status, Pageable pageable);
}
