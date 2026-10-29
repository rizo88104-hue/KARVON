package uz.karvon.catalog.domain;

import java.util.List;

/**
 * One page of search results. {@code page} is zero-based; {@code totalItems} is what the provider reports.
 */
public record SearchResult(List<ProductSummary> items, int page, int pageSize, long totalItems) {
}
