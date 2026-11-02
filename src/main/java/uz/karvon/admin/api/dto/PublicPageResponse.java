package uz.karvon.admin.api.dto;

import java.time.Instant;

/**
 * A page in the language of the Accept-Language header (uz by default, or ru). {@code body} is Markdown.
 */
public record PublicPageResponse(String slug, String lang, String title, String body, Instant updatedAt) {
}
