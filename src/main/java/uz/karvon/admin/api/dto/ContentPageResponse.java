package uz.karvon.admin.api.dto;

import java.time.Instant;
import java.util.UUID;

/**
 * A text page with both languages, for staff.
 */
public record ContentPageResponse(UUID id, String slug, String titleUz, String titleRu, String bodyUz, String bodyRu,
                                  boolean published, Instant createdAt, Instant updatedAt) {
}
