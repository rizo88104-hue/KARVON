package uz.karvon.admin.api.dto;

import java.time.Instant;
import java.util.UUID;

public record TickerMessageResponse(UUID id, String textUz, String textRu, String linkUrl, int sortOrder, boolean active,
                                    Instant createdAt, Instant updatedAt) {
}
