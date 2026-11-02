package uz.karvon.admin.application;

import jakarta.persistence.criteria.Predicate;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import uz.karvon.admin.api.dto.AuditEventResponse;
import uz.karvon.admin.domain.AuditEvent;
import uz.karvon.admin.infrastructure.AuditEventRepository;
import uz.karvon.common.api.PageResponse;

import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * The audit log: written by the {@code @Audited} aspect, read by ADMIN.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class AuditService {

    private final AuditEventRepository events;
    private final Clock clock;

    /** What one audited call did. */
    public record Entry(UUID actorId, String actorRole, String action, String entityType, String entityId,
                        String beforeJson, String afterJson, String ip) {
    }

    /** Filters of the audit list; every field is optional. {@code to} is exclusive. */
    public record Filter(UUID actorId, String action, String entityType, String entityId, Instant from, Instant to) {
    }

    /** In its own transaction: the change it describes is already committed. */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void record(Entry entry) {
        events.save(AuditEvent.builder()
                .actorId(entry.actorId())
                .actorRole(entry.actorRole())
                .action(entry.action())
                .entityType(entry.entityType())
                .entityId(entry.entityId())
                .beforeJson(entry.beforeJson())
                .afterJson(entry.afterJson())
                .ip(entry.ip())
                .createdAt(Instant.now(clock))
                .build());
    }

    /** Newest first. */
    public PageResponse<AuditEventResponse> search(Filter filter, int page, int size) {
        Specification<AuditEvent> where = (root, query, cb) -> {
            List<Predicate> and = new ArrayList<>();
            if (filter.actorId() != null) {
                and.add(cb.equal(root.get("actorId"), filter.actorId()));
            }
            if (filter.action() != null && !filter.action().isBlank()) {
                and.add(cb.equal(root.get("action"), filter.action().trim()));
            }
            if (filter.entityType() != null && !filter.entityType().isBlank()) {
                and.add(cb.equal(root.get("entityType"), filter.entityType().trim()));
            }
            if (filter.entityId() != null && !filter.entityId().isBlank()) {
                and.add(cb.equal(root.get("entityId"), filter.entityId().trim()));
            }
            if (filter.from() != null) {
                and.add(cb.greaterThanOrEqualTo(root.get("createdAt"), filter.from()));
            }
            if (filter.to() != null) {
                and.add(cb.lessThan(root.get("createdAt"), filter.to()));
            }
            return cb.and(and.toArray(Predicate[]::new));
        };
        return PageResponse.of(events.findAll(where, PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt"))),
                e -> new AuditEventResponse(e.getId(), e.getActorId(), e.getActorRole(), e.getAction(),
                        e.getEntityType(), e.getEntityId(), e.getBeforeJson(), e.getAfterJson(), e.getIp(),
                        e.getCreatedAt()));
    }
}
