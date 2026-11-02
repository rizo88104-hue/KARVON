package uz.karvon.admin.api.dto;

import java.time.Instant;
import java.util.UUID;

/**
 * One audit log row. {@code before}/{@code after} are JSON texts (null when there is nothing).
 */
public record AuditEventResponse(UUID id, UUID actorId, String actorRole, String action, String entityType,
                                 String entityId, String before, String after, String ip, Instant createdAt) {
}
