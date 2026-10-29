package uz.karvon.catalog.infrastructure;

import org.springframework.context.annotation.Profile;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.stereotype.Component;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.json.JsonMapper;
import uz.karvon.catalog.application.ProductNotFoundException;
import uz.karvon.catalog.application.TaobaoProvider;
import uz.karvon.catalog.domain.ProductDetail;
import uz.karvon.catalog.domain.ProductSummary;
import uz.karvon.catalog.domain.SearchResult;
import uz.karvon.catalog.domain.ShopInfo;
import uz.karvon.catalog.domain.Sku;
import uz.karvon.catalog.domain.SortOrder;
import uz.karvon.catalog.domain.TaobaoLinks;
import uz.karvon.catalog.domain.TranslatedPair;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Dev and test: products from src/main/resources/fake-taobao/*.json, no network.
 * Search splits the Chinese query on spaces; a product matches when every part is in its title, keywords,
 * attribute or SKU values. A short link (m.tb.cn) always opens the same fake product for the same link.
 */
@Component
@Profile({"dev", "test"})
public class FakeTaobaoProvider implements TaobaoProvider {

    public static final int PAGE_SIZE = 20;

    private final List<FakeProduct> products;
    private final Map<String, FakeProduct> byId;

    public FakeTaobaoProvider(JsonMapper json) {
        List<FakeProduct> loaded = new ArrayList<>();
        try {
            Resource[] files = new PathMatchingResourcePatternResolver().getResources("classpath:fake-taobao/*.json");
            for (Resource file : files) {
                try (InputStream in = file.getInputStream()) {
                    loaded.addAll(json.readValue(in, new TypeReference<List<FakeProduct>>() {
                    }));
                }
            }
        } catch (IOException ex) {
            throw new UncheckedIOException(ex);
        }
        loaded.sort(Comparator.comparing(FakeProduct::externalId));
        this.products = List.copyOf(loaded);
        this.byId = products.stream().collect(Collectors.toMap(FakeProduct::externalId, Function.identity()));
    }

    @Override
    public SearchResult search(String chineseQuery, int page, SortOrder sort, BigDecimal minCny, BigDecimal maxCny) {
        List<String> parts = chineseQuery == null || chineseQuery.isBlank()
                ? List.of() : List.of(chineseQuery.trim().split("\\s+"));
        Stream<FakeProduct> found = products.stream()
                .filter(product -> parts.stream().allMatch(product.searchText()::contains))
                .filter(product -> minCny == null || product.priceCny().compareTo(minCny) >= 0)
                .filter(product -> maxCny == null || product.priceCny().compareTo(maxCny) <= 0);
        List<FakeProduct> sorted = switch (sort) {
            case PRICE_ASC -> found.sorted(Comparator.comparing(FakeProduct::priceCny)).toList();
            case PRICE_DESC -> found.sorted(Comparator.comparing(FakeProduct::priceCny).reversed()).toList();
            case SALES -> found.sorted(Comparator.comparingLong(FakeProduct::salesCount).reversed()).toList();
            case RELEVANCE -> found.toList();
        };
        List<ProductSummary> items = sorted.stream()
                .skip((long) page * PAGE_SIZE)
                .limit(PAGE_SIZE)
                .map(FakeProduct::summary)
                .toList();
        return new SearchResult(items, page, PAGE_SIZE, sorted.size());
    }

    @Override
    public ProductDetail getProduct(String externalId) {
        FakeProduct product = byId.get(externalId);
        if (product == null) {
            throw new ProductNotFoundException();
        }
        return product.detail();
    }

    @Override
    public Optional<String> resolveExternalId(String url) {
        return TaobaoLinks.itemId(url).or(() -> TaobaoLinks.shortLink(url)
                .map(link -> products.get(Math.floorMod(link.getPath().hashCode(), products.size())).externalId()));
    }

    public List<String> productIds() {
        return products.stream().map(FakeProduct::externalId).toList();
    }

    record FakeProduct(String externalId, String title, String mainImageUrl, List<String> images, BigDecimal priceCny,
                       long salesCount, FakeShop shop, BigDecimal chinaShippingCny, BigDecimal weightKg,
                       List<String> keywords, List<FakePair> attributes, List<FakeSku> skus) {

        String searchText() {
            StringBuilder text = new StringBuilder(title);
            keywords.forEach(word -> text.append(' ').append(word));
            attributes.forEach(pair -> text.append(' ').append(pair.value()));
            skus.forEach(sku -> sku.properties().forEach(pair -> text.append(' ').append(pair.value())));
            return text.toString();
        }

        ProductSummary summary() {
            return new ProductSummary(externalId, title, null, mainImageUrl, priceCny, salesCount, shop.name());
        }

        ProductDetail detail() {
            return new ProductDetail(externalId, title, null, mainImageUrl, images, priceCny, salesCount,
                    new ShopInfo(shop.shopId(), shop.name()),
                    attributes.stream().map(FakePair::pair).toList(),
                    skus.stream().map(FakeSku::sku).toList(),
                    chinaShippingCny, weightKg);
        }
    }

    record FakeShop(String shopId, String name) {
    }

    record FakePair(String name, String value) {
        TranslatedPair pair() {
            return TranslatedPair.original(name, value);
        }
    }

    record FakeSku(String skuId, List<FakePair> properties, BigDecimal priceCny, int stock, String imageUrl) {
        Sku sku() {
            return new Sku(skuId, properties.stream().map(FakePair::pair).toList(), priceCny, stock, imageUrl);
        }
    }
}
