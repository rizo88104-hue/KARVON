package uz.karvon.catalog.infrastructure;

import io.github.resilience4j.circuitbreaker.CallNotPermittedException;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.retry.Retry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import uz.karvon.catalog.application.CatalogUnavailableException;
import uz.karvon.catalog.application.ProductNotFoundException;
import uz.karvon.catalog.application.TaobaoProvider;
import uz.karvon.catalog.domain.ProductDetail;
import uz.karvon.catalog.domain.SearchResult;
import uz.karvon.catalog.domain.SortOrder;
import uz.karvon.catalog.domain.TaobaoLinks;

import java.math.BigDecimal;
import java.util.Optional;
import java.util.function.Supplier;

/**
 * A third-party Taobao API over HTTP (TAOBAO_API_URL, TAOBAO_API_KEY). Endpoints: GET /item_search and
 * GET /item_get with the key as the "key" query parameter; the JSON is read by {@link TaobaoResponseMapper}.
 * Every call: 5 s timeout, up to 2 retries on network errors, 5xx and 429, inside a circuit breaker that
 * stops calling a failing API for a while (then the catalog answers CATALOG_UNAVAILABLE at once).
 */
public class HttpTaobaoProvider implements TaobaoProvider {

    private static final Logger log = LoggerFactory.getLogger(HttpTaobaoProvider.class);

    private final RestClient http;
    private final String apiKey;
    private final TaobaoResponseMapper mapper;
    private final CircuitBreaker circuitBreaker;
    private final Retry retry;
    private final ShortLinkResolver shortLinks;

    public HttpTaobaoProvider(RestClient http, String apiKey, TaobaoResponseMapper mapper,
                              CircuitBreaker circuitBreaker, Retry retry, ShortLinkResolver shortLinks) {
        this.http = http;
        this.apiKey = apiKey;
        this.mapper = mapper;
        this.circuitBreaker = circuitBreaker;
        this.retry = retry;
        this.shortLinks = shortLinks;
    }

    @Override
    public SearchResult search(String chineseQuery, int page, SortOrder sort, BigDecimal minCny, BigDecimal maxCny) {
        return call(() -> mapper.toSearchResult(http.get()
                .uri(uri -> {
                    uri.path("/item_search")
                            .queryParam("key", apiKey)
                            .queryParam("q", chineseQuery)
                            .queryParam("page", mapper.pageParam(page))
                            .queryParam("sort", mapper.sortParam(sort));
                    if (minCny != null) {
                        uri.queryParam("start_price", minCny.toPlainString());
                    }
                    if (maxCny != null) {
                        uri.queryParam("end_price", maxCny.toPlainString());
                    }
                    return uri.build();
                })
                .retrieve()
                .body(String.class), page));
    }

    @Override
    public ProductDetail getProduct(String externalId) {
        return call(() -> mapper.toProductDetail(http.get()
                .uri(uri -> uri.path("/item_get").queryParam("key", apiKey).queryParam("num_iid", externalId).build())
                .retrieve()
                .body(String.class)));
    }

    @Override
    public Optional<String> resolveExternalId(String url) {
        return TaobaoLinks.itemId(url).or(() -> TaobaoLinks.shortLink(url).flatMap(shortLinks::resolve));
    }

    private <T> T call(Supplier<T> request) {
        Supplier<T> guarded = CircuitBreaker.decorateSupplier(circuitBreaker, Retry.decorateSupplier(retry, request));
        try {
            return guarded.get();
        } catch (ProductNotFoundException ex) {
            throw ex;
        } catch (CallNotPermittedException ex) {
            log.warn("Taobao API circuit is open, not calling it");
            throw new CatalogUnavailableException(ex);
        } catch (RestClientException | IllegalStateException ex) {
            log.error("Taobao API call failed", ex);
            throw new CatalogUnavailableException(ex);
        }
    }
}
