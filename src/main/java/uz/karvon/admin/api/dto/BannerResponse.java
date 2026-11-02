package uz.karvon.admin.api.dto;

import java.time.Instant;
import java.util.UUID;

public record BannerResponse(UUID id, String title, String subtitle, String imageUrl, String linkUrl, int sortOrder,
                             boolean active, Instant activeFrom, Instant activeTo, Instant createdAt,
                             Instant updatedAt) {
}
