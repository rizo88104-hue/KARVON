package uz.karvon.catalog.infrastructure;

import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;
import uz.karvon.catalog.application.ProductNotFoundException;
import uz.karvon.catalog.domain.ProductDetail;
import uz.karvon.catalog.domain.ProductSummary;
import uz.karvon.catalog.domain.SearchResult;
import uz.karvon.catalog.domain.Sku;
import uz.karvon.catalog.domain.SortOrder;

import java.io.IOException;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;

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

    /** Photos are local files of dev-catalog/images, each listed in docs/image-credits.md. */
    @Test
    void everyPhotoIsALocalFreeLicensePhotoWithCredits() throws IOException {
        Path images = Path.of("dev-catalog/images");
        String credits = Files.readString(Path.of("docs/image-credits.md"));
        for (String id : provider.productIds()) {
            ProductDetail product = provider.getProduct(id);
            assertThat(product.images()).as(id).hasSizeBetween(3, 5).contains(product.mainImageUrl());
            List<String> urls = new ArrayList<>(product.images());
            product.skus().stream().map(Sku::imageUrl).filter(Objects::nonNull).forEach(urls::add);
            for (String url : urls) {
                assertThat(url).as(id).startsWith(DevCatalogImages.URL_PREFIX);
                String file = url.substring(DevCatalogImages.URL_PREFIX.length());
                assertThat(images.resolve(file)).as(url).isRegularFile();
                assertThat(credits).as("credit for " + file).contains("`" + file + "`");
            }
        }
    }

    @Test
    void searchesByKeyword() {
        SearchResult first = provider.search("运动鞋", 0, SortOrder.RELEVANCE, null, null);
        List<ProductSummary> all = new ArrayList<>();
        for (int page = 0; page * FakeTaobaoProvider.PAGE_SIZE < first.totalItems(); page++) {
            all.addAll(provider.search("运动鞋", page, SortOrder.RELEVANCE, null, null).items());
        }

        assertThat(all).isNotEmpty().hasSize((int) first.totalItems())
                .allMatch(item -> item.titleOriginal().contains("运动鞋"));
    }

    @Test
    void everyPartOfTheQueryMustMatchIncludingSkuValues() {
        SearchResult blackSneakers = provider.search("黑色 运动鞋", 0, SortOrder.RELEVANCE, null, null);
        SearchResult sneakers = provider.search("运动鞋", 0, SortOrder.RELEVANCE, null, null);

        assertThat(blackSneakers.items()).isNotEmpty();
        assertThat(blackSneakers.totalItems()).isLessThan(sneakers.totalItems());
        assertThat(provider.search("运动鞋 不存在的词", 0, SortOrder.RELEVANCE, null, null).items()).isEmpty();
    }

    @Test
    void pagesHoldTwentyItems() {
        int total = provider.productIds().size();
        int lastPage = (total - 1) / FakeTaobaoProvider.PAGE_SIZE;
        SearchResult first = provider.search("", 0, SortOrder.RELEVANCE, null, null);
        SearchResult last = provider.search("", lastPage, SortOrder.RELEVANCE, null, null);

        assertThat(first.items()).hasSize(FakeTaobaoProvider.PAGE_SIZE);
        assertThat(first.totalItems()).isEqualTo(total);
        assertThat(last.items()).hasSize(total - lastPage * FakeTaobaoProvider.PAGE_SIZE);
        assertThat(provider.search("", lastPage + 1, SortOrder.RELEVANCE, null, null).items()).isEmpty();
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
