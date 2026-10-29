package uz.karvon.order.api.dto;

import java.time.Instant;
import java.util.UUID;

public record OrderCommentResponse(UUID id, UUID authorId, String text, Instant createdAt) {
}
