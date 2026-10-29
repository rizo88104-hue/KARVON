package uz.karvon.catalog.api;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.web.servlet.assertj.MockMvcTester;
import org.springframework.test.web.servlet.assertj.MvcTestResult;
import uz.karvon.AbstractIntegrationTest;
import uz.karvon.catalog.infrastructure.FakeTaobaoProvider;
import uz.karvon.translation.application.TranslationService;
import uz.karvon.translation.domain.Lang;
import uz.karvon.translation.infrastructure.FakeTranslator;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static uz.karvon.TestLogins.read;

/**
 * The catalog with the fake provider and fake translator (test profile). Both are spies, to count real calls.
 */
class CatalogIntegrationTest extends AbstractIntegrationTest {

    /** 男士运动鞋透气网面跑步鞋, 129 CNY, 0.9 kg; SKUs black/white/grey x sizes 39-44. */
    private static final String SNEAKERS = "680000001117";

    @Autowired
    private MockMvcTester mvc;

    @MockitoSpyBean
    private FakeTaobaoProvider provider;

    @MockitoSpyBean
    private FakeTranslator translator;

    @Autowired
    private TranslationService translation;

    @Autowired
    private StringRedisTemplate redis;

    @Autowired
    private JdbcTemplate jdbc;

    @Test
    void searchInUzbekReturnsTranslatedProductsWithUzsPrices() {
        MvcTestResult result = mvc.get().uri("/api/v1/catalog/search?q=krossovka").exchange();

        assertThat(result).hasStatusOk();
        assertThat(result).bodyJson().extractingPath("$.content.length()").isEqualTo(3);
        assertThat(result).bodyJson().extractingPath("$.content[0].externalId").isEqualTo(SNEAKERS);
        assertThat(result).bodyJson().extractingPath("$.content[0].titleOriginal").isEqualTo("男士运动鞋透气网面跑步鞋");
        assertThat(result).bodyJson().extractingPath("$.content[0].title")
                .isEqualTo("Erkaklar sport krossovkasi, nafas oladigan to'r matoli");
        // 129 CNY, no China shipping, default weight 0.5 kg: 229 600 + 23 000 + 40 000
        assertThat(result).bodyJson().extractingPath("$.content[0].priceUzs").isEqualTo(292600);
        assertThat(result).bodyJson().extractingPath("$.size").isEqualTo(FakeTaobaoProvider.PAGE_SIZE);
        assertThat(result).bodyJson().extractingPath("$.totalElements").isEqualTo(3);
    }

    @Test
    void searchInRussianAndInChinese() {
        var russian = mvc.get().uri("/api/v1/catalog/search?q=кроссовки").header(HttpHeaders.ACCEPT_LANGUAGE, "ru");
        assertThat(russian).bodyJson().extractingPath("$.content[0].title")
                .isEqualTo("Мужские спортивные кроссовки, дышащая сетка");

        var chinese = mvc.get().uri("/api/v1/catalog/search?q=运动鞋");
        assertThat(chinese).bodyJson().extractingPath("$.content.length()").isEqualTo(3);
    }

    @Test
    void sortAndPriceFilterInUzs() {
        // 50 000 - 200 000 UZS of goods = about 28 - 112 CNY at 1780
        var result = mvc.get().uri("/api/v1/catalog/search?q=sumka&sort=PRICE_ASC&minPrice=50000&maxPrice=200000");

        assertThat(result).hasStatusOk();
        assertThat(result).bodyJson().extractingPath("$.content[*].priceCny").asArray()
                .isNotEmpty()
                .isSortedAccordingTo((a, b) -> Double.compare(((Number) a).doubleValue(), ((Number) b).doubleValue()));
    }

    @Test
    void blockedProductsAreHiddenFromSearch() {
        // 高仿名牌女包 is a fake product with a blocked keyword; every other bag is shown.
        var bags = mvc.get().uri("/api/v1/catalog/search?q=sumka");
        assertThat(bags).hasStatusOk();
        assertThat(bags).bodyJson().extractingPath("$.content[*].titleOriginal").asArray()
                .isNotEmpty()
                .noneMatch(title -> title.toString().contains("高仿"));

        // A blocked query (atir = perfume) gives nothing and does not even reach the provider.
        var perfume = mvc.get().uri("/api/v1/catalog/search?q=atir");
        assertThat(perfume).bodyJson().extractingPath("$.content").asArray().isEmpty();
        verify(provider, times(0)).search(eq("香水"), anyInt(), any(), any(), any());
    }

    @Test
    void blockedProductCannotBeOpened() {
        String perfumeId = provider.productIds().stream()
                .filter(id -> provider.getProduct(id).titleOriginal().contains("香水"))
                .findFirst().orElseThrow();

        var result = mvc.get().uri("/api/v1/catalog/products/{id}", perfumeId);

        assertThat(result).hasStatus(HttpStatus.UNPROCESSABLE_CONTENT);
        assertThat(result).bodyJson().extractingPath("$.code").isEqualTo("PRODUCT_BLOCKED");
    }

    @Test
    void productDetailHasTranslatedSkusEachWithAPrice() {
        MvcTestResult result = mvc.get().uri("/api/v1/catalog/products/{id}", SNEAKERS).exchange();

        assertThat(result).hasStatusOk();
        assertThat(result).bodyJson().extractingPath("$.title").isEqualTo("Erkaklar sport krossovkasi, nafas oladigan to'r matoli");
        assertThat(result).bodyJson().extractingPath("$.skus.length()").isEqualTo(18);
        assertThat(result).bodyJson().extractingPath("$.skus[0].properties[0].nameOriginal").isEqualTo("颜色分类");
        assertThat(result).bodyJson().extractingPath("$.skus[0].properties[0].name").isEqualTo("Rang");
        assertThat(result).bodyJson().extractingPath("$.skus[0].properties[0].value").isEqualTo("Qora");
        // 129 CNY, real weight 0.9 kg: 229 600 + 23 000 + 72 000
        assertThat(result).bodyJson().extractingPath("$.skus[0].price.totalUzs").isEqualTo(324600);
        assertThat(result).bodyJson().extractingPath("$.skus[0].price.settingsId").asString().isNotBlank();
        assertThat(result).bodyJson().extractingPath("$.price.totalUzs").isEqualTo(324600);
        assertThat(result).bodyJson().extractingPath("$.attributes[1].name").isEqualTo("Material");
        assertThat(result).bodyJson().extractingPath("$.weightEstimated").isEqualTo(false);
    }

    @Test
    void productDetailIsCachedForTwoHours() {
        redis.delete("catalog:product:" + SNEAKERS);

        assertThat(mvc.get().uri("/api/v1/catalog/products/{id}", SNEAKERS)).hasStatusOk();
        assertThat(mvc.get().uri("/api/v1/catalog/products/{id}", SNEAKERS)).hasStatusOk();

        verify(provider, times(1)).getProduct(SNEAKERS);
        assertThat(redis.getExpire("catalog:product:" + SNEAKERS, TimeUnit.MINUTES)).isBetween(118L, 120L);
    }

    @Test
    void searchIsCachedForThirtyMinutes() {
        redis.keys("catalog:search:*").forEach(redis::delete);

        mvc.get().uri("/api/v1/catalog/search?q=quloqchin").exchange();
        mvc.get().uri("/api/v1/catalog/search?q=quloqchin").exchange();
        mvc.get().uri("/api/v1/catalog/search?q=quloqchin").header(HttpHeaders.ACCEPT_LANGUAGE, "ru").exchange();

        verify(provider, times(1)).search(eq("耳机"), anyInt(), any(), any(), any());
        String key = redis.keys("catalog:search:*").iterator().next();
        assertThat(redis.getExpire(key, TimeUnit.MINUTES)).isBetween(28L, 30L);
    }

    @Test
    void eachStringIsTranslatedOnlyOnce() {
        String text = "test-" + UUID.randomUUID();

        assertThat(translation.translate(text, Lang.ZH, Lang.UZ)).isEqualTo(text);
        assertThat(translation.translateBatch(List.of(text, text), Lang.ZH, Lang.UZ)).containsExactly(text, text);

        verify(translator, times(1)).translateBatch(List.of(text), Lang.ZH, Lang.UZ);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM translation_cache WHERE text = ?", Integer.class, text))
                .isEqualTo(1);
    }

    @Test
    void unknownProductIs404() {
        assertThat(mvc.get().uri("/api/v1/catalog/products/{id}", "123456789")).hasStatus(HttpStatus.NOT_FOUND);
        assertThat(mvc.get().uri("/api/v1/catalog/products/{id}", "not-an-id"))
                .bodyJson().extractingPath("$.code").isEqualTo("PRODUCT_NOT_FOUND");
    }

    @Test
    void resolvesPastedLinks() {
        assertThat(resolve("https://item.taobao.com/item.htm?spm=a1z10&id=680000001117"))
                .bodyJson().extractingPath("$.externalId").isEqualTo(SNEAKERS);
        assertThat(resolve("【淘宝】 https://m.tb.cn/h.5AbCdEf?tk=xyz 「男士运动鞋」"))
                .bodyJson().extractingPath("$.externalId").asString().matches("\\d+");

        MvcTestResult unknown = resolve("https://www.amazon.com/dp/B000123");
        assertThat(unknown).hasStatus(HttpStatus.BAD_REQUEST);
        assertThat(unknown).bodyJson().extractingPath("$.code").isEqualTo("LINK_NOT_RECOGNIZED");
    }

    @Test
    void categoriesAreATreeWithBothLanguagesAndChineseKeyword() {
        var result = mvc.get().uri("/api/v1/catalog/categories");

        assertThat(result).hasStatusOk();
        assertThat(result).bodyJson().extractingPath("$.length()").isEqualTo(7);
        assertThat(result).bodyJson().extractingPath("$[1].slug").isEqualTo("poyabzal");
        assertThat(result).bodyJson().extractingPath("$[1].nameRu").isEqualTo("Обувь");
        assertThat(result).bodyJson().extractingPath("$[1].children[0].nameUz").isEqualTo("Krossovkalar");
        assertThat(result).bodyJson().extractingPath("$[1].children[0].searchKeywordZh").isEqualTo("运动鞋");
    }

    @Test
    void searchNeedsAQuery() {
        assertThat(mvc.get().uri("/api/v1/catalog/search?q=")).hasStatus(HttpStatus.BAD_REQUEST);
        assertThat(mvc.get().uri("/api/v1/catalog/search?q=a&page=500")).hasStatus(HttpStatus.BAD_REQUEST);
    }

    private MvcTestResult resolve(String url) {
        return mvc.post().uri("/api/v1/catalog/resolve-link").contentType(MediaType.APPLICATION_JSON)
                .content("{\"url\":\"%s\"}".formatted(url.replace("\"", "\\\""))).exchange();
    }
}
