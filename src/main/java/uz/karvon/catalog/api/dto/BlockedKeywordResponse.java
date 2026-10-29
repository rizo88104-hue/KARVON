package uz.karvon.catalog.api.dto;

import uz.karvon.catalog.domain.BlockedCategory;
import uz.karvon.translation.domain.Lang;

import java.time.Instant;
import java.util.UUID;

public record BlockedKeywordResponse(UUID id, String keyword, Lang lang, BlockedCategory category, Instant createdAt) {
}
