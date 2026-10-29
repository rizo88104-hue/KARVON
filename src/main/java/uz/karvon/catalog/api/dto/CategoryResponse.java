package uz.karvon.catalog.api.dto;

import java.util.List;

/**
 * A category and its children. Open it with GET /api/v1/catalog/search?q={searchKeywordZh}.
 */
public record CategoryResponse(String slug, String nameUz, String nameRu, String searchKeywordZh,
                               List<CategoryResponse> children) {
}
