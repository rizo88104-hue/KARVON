package uz.karvon.catalog.api.dto;

import java.util.List;

/**
 * A category and its children. Open it with GET /api/v1/catalog/search?q={searchKeywordZh}.
 * {@code imageUrl} is a cover photo, or null when the category has none (only the dev catalog has covers for now).
 */
public record CategoryResponse(String slug, String nameUz, String nameRu, String searchKeywordZh, String icon,
                               String imageUrl, List<CategoryResponse> children) {
}
