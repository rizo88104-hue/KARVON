package uz.karvon.catalog.api.dto;

import java.util.UUID;

/**
 * A category as ADMIN edits it (flat, with ids).
 */
public record AdminCategoryResponse(UUID id, UUID parentId, String slug, String nameUz, String nameRu,
                                    String searchKeywordZh, String icon, int sortOrder) {
}
