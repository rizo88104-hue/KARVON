package uz.karvon.catalog.infrastructure;

import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerConfig;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;
import tools.jackson.databind.json.JsonMapper;
import uz.karvon.catalog.application.CatalogUnavailableException;
import uz.karvon.catalog.application.ProductNotFoundException;
import uz.karvon.catalog.domain.ProductDetail;
import uz.karvon.catalog.domain.SearchResult;
import uz.karvon.catalog.domain.SortOrder;
import uz.karvon.catalog.domain.TranslatedPair;

import java.math.BigDecimal;
import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.ExpectedCount.never;
import static org.springframework.test.web.client.ExpectedCount.once;
import static org.springframework.test.web.client.ExpectedCount.times;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.queryParam;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestToUriTemplate;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

class HttpTaobaoProviderTest {

    private static final String BASE = "https://taobao-api.test";

    static final String SEARCH = """
            {"items":{"page":"1","page_size":"40","real_total_results":"1234",
              "item":[{"num_iid":"681234567890","title":"男士运动鞋","pic_url":"//img.alicdn.com/a.jpg","price":"129.00",
                       "promotion_price":"99.00","sales":1523,"seller_nick":"运动鞋店"},
                      {"num_iid":"681234567891","title":"女士运动鞋","pic_url":"https://img.alicdn.com/b.jpg","price":"89",
                       "sales":"77","nick":"女鞋店"}]},
             "error":"","error_code":"0000"}""";

    static final String PRODUCT = """
            {"item":{"num_iid":"681234567890","title":"男士运动鞋","price":"129.00","pic_url":"//img.alicdn.com/main.jpg",
              "item_imgs":[{"url":"//img.alicdn.com/1.jpg"},{"url":"//img.alicdn.com/2.jpg"}],
              "props":[{"name":"品牌","value":"运动优选"},{"name":"材质","value":"网面"}],
              "nick":"运动鞋店","shop_id":"5551","post_fee":"8.00","item_weight":"0.9","total_sold":"15230",
              "props_img":{"1627207:28341":"//img.alicdn.com/black.jpg"},
              "skus":{"sku":[
                {"sku_id":"5001","price":"129.00","quantity":"120","properties":"1627207:28341;20509:28314",
                 "properties_name":"1627207:28341:颜色分类:黑色;20509:28314:鞋码:42"},
                {"sku_id":"5002","price":"119.00","quantity":"0","properties":"1627207:28342;20509:28314",
                 "properties_name":"1627207:28342:颜色分类:白色;20509:28314:鞋码:42"}]}},
             "error_code":"0000"}""";

    private final RestClient.Builder builder = RestClient.builder().baseUrl(BASE);
    private final MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
    private final CircuitBreaker breaker = CircuitBreaker.of("test", CircuitBreakerConfig.custom()
            .slidingWindowSize(4).minimumNumberOfCalls(2).failureRateThreshold(50)
            .waitDurationInOpenState(Duration.ofMinutes(1))
            .ignoreExceptions(ProductNotFoundException.class).build());
    private final HttpTaobaoProvider provider = new HttpTaobaoProvider(builder.build(), "secret-key",
            new TaobaoResponseMapper(JsonMapper.builder().build()), breaker, TaobaoConfig.retry(Duration.ofMillis(1)),
            new ShortLinkResolver(RestClient.builder()));

    @Test
    void searchSendsQueryAndMapsItems() {
        server.expect(once(), requestToUriTemplate(BASE + "/item_search?key={k}&q={q}&page=1&sort=_sale&start_price=10&end_price=200",
                        "secret-key", "运动鞋"))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withSuccess(SEARCH, MediaType.APPLICATION_JSON));

        SearchResult result = provider.search("运动鞋", 0, SortOrder.SALES, BigDecimal.TEN, new BigDecimal("200"));

        assertThat(result.totalItems()).isEqualTo(1234);
        assertThat(result.pageSize()).isEqualTo(40);
        assertThat(result.page()).isZero();
        assertThat(result.items()).hasSize(2);
        assertThat(result.items().getFirst().priceCny()).isEqualByComparingTo("99.00");
        assertThat(result.items().getFirst().mainImageUrl()).isEqualTo("https://img.alicdn.com/a.jpg");
        assertThat(result.items().get(1).shopName()).isEqualTo("女鞋店");
        assertThat(result.items().get(1).salesCount()).isEqualTo(77);
    }

    @Test
    void productIsMappedWithSkusAttributesAndWeight() {
        server.expect(once(), requestTo(BASE + "/item_get?key=secret-key&num_iid=681234567890"))
                .andRespond(withSuccess(PRODUCT, MediaType.APPLICATION_JSON));

        ProductDetail product = provider.getProduct("681234567890");

        assertThat(product.priceCny()).as("lowest SKU price").isEqualByComparingTo("119.00");
        assertThat(product.chinaShippingCny()).isEqualByComparingTo("8.00");
        assertThat(product.weightKg()).isEqualByComparingTo("0.9");
        assertThat(product.salesCount()).isEqualTo(15230);
        assertThat(product.shop().name()).isEqualTo("运动鞋店");
        assertThat(product.images()).containsExactly("https://img.alicdn.com/1.jpg", "https://img.alicdn.com/2.jpg");
        assertThat(product.attributes()).containsExactly(TranslatedPair.original("品牌", "运动优选"),
                TranslatedPair.original("材质", "网面"));
        assertThat(product.skus()).hasSize(2);
        assertThat(product.skus().getFirst().properties())
                .containsExactly(TranslatedPair.original("颜色分类", "黑色"), TranslatedPair.original("鞋码", "42"));
        assertThat(product.skus().getFirst().imageUrl()).isEqualTo("https://img.alicdn.com/black.jpg");
        assertThat(product.skus().get(1).stock()).isZero();
    }

    @Test
    void productWithoutVariantsGetsOneSku() {
        server.expect(once(), requestTo(BASE + "/item_get?key=secret-key&num_iid=681234567890"))
                .andRespond(withSuccess("""
                        {"item":{"num_iid":"681234567890","title":"杯子","price":"15.5","num":"30"},"error_code":"0000"}""",
                        MediaType.APPLICATION_JSON));

        ProductDetail product = provider.getProduct("681234567890");

        assertThat(product.skus()).singleElement()
                .satisfies(sku -> assertThat(sku.skuId()).isEqualTo("681234567890"))
                .satisfies(sku -> assertThat(sku.stock()).isEqualTo(30));
        assertThat(product.weightKg()).isNull();
        assertThat(product.chinaShippingCny()).isEqualByComparingTo("0");
    }

    @Test
    void missingProductIsNotFoundWithoutRetries() {
        server.expect(once(), requestTo(BASE + "/item_get?key=secret-key&num_iid=681234567890"))
                .andRespond(withSuccess("{\"error\":\"item-not-found\",\"error_code\":\"2000\"}", MediaType.APPLICATION_JSON));

        assertThatThrownBy(() -> provider.getProduct("681234567890")).isInstanceOf(ProductNotFoundException.class);
        server.verify();
        assertThat(breaker.getState()).isEqualTo(CircuitBreaker.State.CLOSED);
    }

    @Test
    void serverErrorsAreRetriedTwice() {
        server.expect(times(2), requestTo(BASE + "/item_get?key=secret-key&num_iid=681234567890"))
                .andRespond(withServerError());
        server.expect(once(), requestTo(BASE + "/item_get?key=secret-key&num_iid=681234567890"))
                .andRespond(withSuccess(PRODUCT, MediaType.APPLICATION_JSON));

        assertThat(provider.getProduct("681234567890").externalId()).isEqualTo("681234567890");
        server.verify();
    }

    @Test
    void afterThreeFailuresTheCallFailsAndClientErrorsAreNotRetried() {
        server.expect(times(3), requestTo(BASE + "/item_get?key=secret-key&num_iid=681234567890"))
                .andRespond(withStatus(HttpStatus.BAD_GATEWAY));

        assertThatThrownBy(() -> provider.getProduct("681234567890"))
                .isInstanceOf(CatalogUnavailableException.class)
                .hasFieldOrPropertyWithValue("code", "CATALOG_UNAVAILABLE");
        server.verify();
        server.reset();

        server.expect(once(), requestTo(BASE + "/item_get?key=secret-key&num_iid=681234567891"))
                .andRespond(withStatus(HttpStatus.FORBIDDEN));
        assertThatThrownBy(() -> provider.getProduct("681234567891")).isInstanceOf(CatalogUnavailableException.class);
        server.verify();
    }

    @Test
    void openCircuitStopsCallingTheApi() {
        server.expect(times(6), requestTo(BASE + "/item_get?key=secret-key&num_iid=681234567890"))
                .andRespond(withServerError());
        for (int i = 0; i < 2; i++) {
            assertThatThrownBy(() -> provider.getProduct("681234567890")).isInstanceOf(CatalogUnavailableException.class);
        }
        server.verify();
        server.reset();
        server.expect(never(), requestTo(BASE + "/item_get?key=secret-key&num_iid=681234567890"));

        assertThat(breaker.getState()).isEqualTo(CircuitBreaker.State.OPEN);
        assertThatThrownBy(() -> provider.getProduct("681234567890")).isInstanceOf(CatalogUnavailableException.class);
        server.verify();
    }

    @Test
    void brokenJsonIsUnavailable() {
        server.expect(once(), requestTo(BASE + "/item_get?key=secret-key&num_iid=681234567890"))
                .andRespond(withSuccess("<html>maintenance</html>", MediaType.TEXT_HTML));

        assertThatThrownBy(() -> provider.getProduct("681234567890")).isInstanceOf(CatalogUnavailableException.class);
    }

    @Test
    void plainLinksAreResolvedWithoutHttp() {
        assertThat(provider.resolveExternalId("https://detail.tmall.com/item.htm?id=681234567890")).hasValue("681234567890");
    }

    @Test
    void shortLinkIsOpenedOnce() {
        RestClient.Builder shortLinkBuilder = RestClient.builder();
        MockRestServiceServer shortLinkServer = MockRestServiceServer.bindTo(shortLinkBuilder).build();
        HttpTaobaoProvider withShortLinks = new HttpTaobaoProvider(builder.build(), "secret-key",
                new TaobaoResponseMapper(JsonMapper.builder().build()), breaker, TaobaoConfig.retry(Duration.ofMillis(1)),
                new ShortLinkResolver(shortLinkBuilder));
        shortLinkServer.expect(once(), requestTo("https://m.tb.cn/h.5AbCdEf?tk=xyz"))
                .andRespond(withStatus(HttpStatus.FOUND).header(HttpHeaders.LOCATION,
                        "https://item.taobao.com/item.htm?id=681234567890&ut_sk=1"));

        assertThat(withShortLinks.resolveExternalId("【淘宝】 https://m.tb.cn/h.5AbCdEf?tk=xyz 「男士运动鞋」"))
                .hasValue("681234567890");
        shortLinkServer.verify();
    }
}
