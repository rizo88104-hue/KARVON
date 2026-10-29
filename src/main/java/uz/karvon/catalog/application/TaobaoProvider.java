package uz.karvon.catalog.application;

import uz.karvon.catalog.domain.ProductDetail;
import uz.karvon.catalog.domain.SearchResult;
import uz.karvon.catalog.domain.SortOrder;

import java.math.BigDecimal;
import java.util.Optional;

/**
 * A source of Taobao data. Returns Chinese texts only; translation and caching are done by {@link CatalogService}.
 * Throws {@link ProductNotFoundException} for a missing product and {@link CatalogUnavailableException}
 * when the source cannot be reached.
 */
public interface TaobaoProvider {

    /** {@code page} is zero-based; {@code minCny}/{@code maxCny} may be null. */
    SearchResult search(String chineseQuery, int page, SortOrder sort, BigDecimal minCny, BigDecimal maxCny);

    ProductDetail getProduct(String externalId);

    /** The item id of a pasted link: item.taobao.com, detail.tmall.com, m.tb.cn short links. */
    Optional<String> resolveExternalId(String url);
}
