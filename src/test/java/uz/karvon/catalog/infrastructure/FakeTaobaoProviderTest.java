package uz.karvon.catalog.infrastructure;

import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;
import uz.karvon.catalog.application.ProductNotFoundException;
import uz.karvon.catalog.domain.ProductDetail;
import uz.karvon.catalog.domain.ProductSummary;
import uz.karvon.catalog.domain.SearchResult;
import uz.karvon.catalog.domain.SortOrder;

import java.math.BigDecimal;
import java.util.Comparator;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class FakeTaobaoProviderTest {

    private final FakeTaobaoProvider provider = new FakeTaobaoProvider(JsonMapper.builder().build());

    @Test
    void hasAtLeastFortyProductsEachWithSkus() {
        assertThat(provider.productIds()).hasSizeGreaterThanOrEqualTo(40).doesNotHaveDuplicates();
        for (String id : provider.productIds()) {
            ProductDetail product = provider.getProduct(id);
            assertThat(product.skus()).as(id).isNotEmpty();
            assertThat(product.images()).as(id).isNotEmpty();
            assertThat(product.priceCny()).as(id).isPositive();
        }
    }

    @Test
    void searchesByKeyword() {
        SearchResult result = provider.search("运动鞋", 0, SortOrder.RELEVANCE, null, null);

        assertThat(result.items()).isNotEmpty().allMatch(item -> item.titleOriginal().contains("运动鞋"));
        assertThat(result.totalItems()).isEqualTo(result.items().size());
    }

    @Test
    void everyPartOfTheQueryMustMatchIncludingSkuValues() {
        List<ProductSummary> blackSneakers = provider.search("黑色 运动鞋", 0, SortOrder.RELEVANCE, null, null).items();
        List<ProductSummary> sneakers = provider.search("运动鞋", 0, SortOrder.RELEVANCE, null, null).items();

        assertThat(blackSneakers).isNotEmpty().hasSizeLessThan(sneakers.size());
        assertThat(provider.search("运动鞋 不存在的词", 0, SortOrder.RELEVANCE, null, null).items()).isEmpty();
    }

    @Test
    void pagesHoldTwentyItems() {
        SearchResult first = provider.search("", 0, SortOrder.RELEVANCE, null, null);
        SearchResult third = provider.search("", 2, SortOrder.RELEVANCE, null, null);

        assertThat(first.items()).hasSize(FakeTaobaoProvider.PAGE_SIZE);
        assertThat(first.totalItems()).isEqualTo(provider.productIds().size());
        assertThat(third.items()).hasSize(provider.productIds().size() - 2 * FakeTaobaoProvider.PAGE_SIZE);
        assertThat(provider.search("", 5, SortOrder.RELEVANCE, null, null).items()).isEmpty();
    }

    @Test
    void sortsAndFiltersByPrice() {
        List<ProductSummary> cheapFirst = provider.search("", 0, SortOrder.PRICE_ASC, new BigDecimal("50"),
                new BigDecimal("150")).items();

        assertThat(cheapFirst).isNotEmpty()
                .isSortedAccordingTo(Comparator.comparing(ProductSummary::priceCny))
                .allMatch(item -> item.priceCny().compareTo(new BigDecimal("50")) >= 0
                        && item.priceCny().compareTo(new BigDecimal("150")) <= 0);
        assertThat(provider.search("", 0, SortOrder.SALES, null, null).items())
                .isSortedAccordingTo(Comparator.comparingLong(ProductSummary::salesCount).reversed());
    }

    @Test
    void unknownProductIsNotFound() {
        assertThatThrownBy(() -> provider.getProduct("1")).isInstanceOf(ProductNotFoundException.class);
    }

    @Test
    void resolvesLinksAndShortLinks() {
        assertThat(provider.resolveExternalId("https://item.taobao.com/item.htm?id=681234567890")).hasValue("681234567890");
        String fromShortLink = provider.resolveExternalId("https://m.tb.cn/h.5AbCdEf?tk=1").orElseThrow();
        assertThat(provider.productIds()).contains(fromShortLink);
        assertThat(provider.resolveExternalId("https://m.tb.cn/h.5AbCdEf")).hasValue(fromShortLink);
        assertThat(provider.resolveExternalId("https://amazon.com/dp/1")).isEmpty();
    }
}
