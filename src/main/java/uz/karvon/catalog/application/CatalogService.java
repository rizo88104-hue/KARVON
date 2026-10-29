package uz.karvon.catalog.application;

import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import uz.karvon.catalog.api.dto.CategoryResponse;
import uz.karvon.catalog.api.dto.ProductCardResponse;
import uz.karvon.catalog.api.dto.ProductDetailResponse;
import uz.karvon.catalog.api.dto.SkuResponse;
import uz.karvon.catalog.domain.Category;
import uz.karvon.catalog.domain.ProductDetail;
import uz.karvon.catalog.domain.ProductSummary;
import uz.karvon.catalog.domain.SearchResult;
import uz.karvon.catalog.domain.Sku;
import uz.karvon.catalog.domain.SortOrder;
import uz.karvon.catalog.domain.TranslatedPair;
import uz.karvon.catalog.infrastructure.CatalogCache;
import uz.karvon.catalog.infrastructure.CategoryRepository;
import uz.karvon.common.api.PageResponse;
import uz.karvon.common.application.BusinessException;
import uz.karvon.common.domain.Hashes;
import uz.karvon.pricing.application.PriceQuoter;
import uz.karvon.pricing.application.PricingService;
import uz.karvon.translation.application.TranslationService;
import uz.karvon.translation.domain.Lang;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.regex.Pattern;

/**
 * The public catalog. Search: the customer's query (uz/ru) is translated to Chinese, the provider is asked
 * (through the Redis cache), blocked goods are removed, titles are translated back in one batch and every
 * item gets a UZS price. Translations go through the translation cache, so each string is translated once.
 */
@Service
@RequiredArgsConstructor
public class CatalogService {

    private static final Pattern HAN = Pattern.compile("\\p{IsHan}");
    private static final Pattern EXTERNAL_ID = Pattern.compile("\\d{5,20}");

    private final TaobaoProvider provider;
    private final CatalogCache cache;
    private final TranslationService translation;
    private final BlockedGoodsService blockedGoods;
    private final PricingService pricing;
    private final CategoryRepository categories;

    /**
     * @param query     in the customer's language, or Chinese (e.g. a category keyword)
     * @param minPriceUzs, maxPriceUzs goods price limits in UZS, turned into CNY with the customer rate; may be null
     */
    public PageResponse<ProductCardResponse> search(String query, int page, SortOrder sort,
                                                    BigDecimal minPriceUzs, BigDecimal maxPriceUzs, Lang lang) {
        String trimmed = query.trim();
        Lang queryLang = HAN.matcher(trimmed).find() ? Lang.ZH : lang;
        String chinese = translation.translate(trimmed, queryLang, Lang.ZH);
        PriceQuoter quoter = pricing.quoter();
        if (blockedGoods.isBlocked(trimmed, chinese)) {
            return new PageResponse<>(List.of(), page, 0, 0, 0);
        }
        BigDecimal minCny = toCny(minPriceUzs, quoter);
        BigDecimal maxCny = toCny(maxPriceUzs, quoter);
        String key = Hashes.sha256Hex(String.join("|", chinese, String.valueOf(page), sort.name(),
                String.valueOf(minCny), String.valueOf(maxCny)));
        SearchResult result = cache.search(key, () -> provider.search(chinese, page, sort, minCny, maxCny));

        List<ProductSummary> visible = result.items().stream()
                .filter(item -> !blockedGoods.isBlocked(item.titleOriginal()))
                .toList();
        List<String> titles = translation.translateBatch(visible.stream().map(ProductSummary::titleOriginal).toList(),
                Lang.ZH, lang);
        List<ProductCardResponse> cards = new ArrayList<>();
        for (int i = 0; i < visible.size(); i++) {
            ProductSummary item = visible.get(i).withTitleTranslated(titles.get(i));
            if (blockedGoods.isBlocked(item.titleTranslated())) {
                continue;
            }
            BigDecimal priceUzs = quoter.price(item.priceCny(), null, 1, null).totalUzs();
            cards.add(new ProductCardResponse(item.externalId(), item.titleTranslated(), item.titleOriginal(),
                    item.mainImageUrl(), item.priceCny(), priceUzs, item.salesCount(), item.shopName()));
        }
        int totalPages = result.pageSize() == 0 ? 0 : (int) Math.ceil((double) result.totalItems() / result.pageSize());
        return new PageResponse<>(cards, page, result.pageSize(), result.totalItems(), totalPages);
    }

    /** A product with translated texts and a price for every SKU. Blocked goods answer PRODUCT_BLOCKED. */
    public ProductDetailResponse product(String externalId, Lang lang) {
        if (!EXTERNAL_ID.matcher(externalId).matches()) {
            throw new ProductNotFoundException();
        }
        ProductDetail original = cache.product(externalId, () -> provider.getProduct(externalId));
        if (blockedGoods.isBlocked(original.titleOriginal())) {
            throw blocked();
        }
        ProductDetail product = translate(original, lang);
        if (blockedGoods.isBlocked(product.titleTranslated())) {
            throw blocked();
        }
        PriceQuoter quoter = pricing.quoter();
        List<SkuResponse> skus = product.skus().stream()
                .map(sku -> new SkuResponse(sku.skuId(), sku.properties(), sku.priceCny(), sku.stock(), sku.imageUrl(),
                        quoter.price(sku.priceCny(), product.chinaShippingCny(), 1, product.weightKg())))
                .toList();
        return new ProductDetailResponse(product.externalId(), product.titleTranslated(), product.titleOriginal(),
                product.mainImageUrl(), product.images(), product.priceCny(),
                quoter.price(product.priceCny(), product.chinaShippingCny(), 1, product.weightKg()),
                product.salesCount(), product.shop(), product.attributes(), skus, product.chinaShippingCny(),
                product.weightKg(), product.weightKg() == null);
    }

    public String resolveLink(String pasted) {
        return provider.resolveExternalId(pasted)
                .orElseThrow(() -> new BusinessException("LINK_NOT_RECOGNIZED", "error.catalog.linkNotRecognized"));
    }

    @Transactional(readOnly = true)
    public List<CategoryResponse> categories() {
        Map<UUID, List<Category>> byParent = new LinkedHashMap<>();
        for (Category category : categories.findAllByOrderBySortOrderAsc()) {
            byParent.computeIfAbsent(category.getParentId(), parent -> new ArrayList<>()).add(category);
        }
        return tree(byParent, null);
    }

    private List<CategoryResponse> tree(Map<UUID, List<Category>> byParent, UUID parentId) {
        return byParent.getOrDefault(parentId, List.of()).stream()
                .sorted(Comparator.comparingInt(Category::getSortOrder))
                .map(c -> new CategoryResponse(c.getSlug(), c.getNameUz(), c.getNameRu(), c.getSearchKeywordZh(),
                        tree(byParent, c.getId())))
                .toList();
    }

    /** Title, attribute names/values and SKU property names/values in one batch. */
    private ProductDetail translate(ProductDetail product, Lang lang) {
        List<String> texts = new ArrayList<>();
        texts.add(product.titleOriginal());
        for (TranslatedPair attribute : product.attributes()) {
            texts.add(attribute.nameOriginal());
            texts.add(attribute.valueOriginal());
        }
        for (Sku sku : product.skus()) {
            for (TranslatedPair property : sku.properties()) {
                texts.add(property.nameOriginal());
                texts.add(property.valueOriginal());
            }
        }
        List<String> translated = translation.translateBatch(texts, Lang.ZH, lang);
        int index = 1;
        List<TranslatedPair> attributes = new ArrayList<>();
        for (TranslatedPair attribute : product.attributes()) {
            attributes.add(attribute.translated(translated.get(index), translated.get(index + 1)));
            index += 2;
        }
        List<Sku> skus = new ArrayList<>();
        for (Sku sku : product.skus()) {
            List<TranslatedPair> properties = new ArrayList<>();
            for (TranslatedPair property : sku.properties()) {
                properties.add(property.translated(translated.get(index), translated.get(index + 1)));
                index += 2;
            }
            skus.add(sku.withProperties(properties));
        }
        return product.translated(translated.getFirst(), attributes, skus);
    }

    private static BigDecimal toCny(BigDecimal uzs, PriceQuoter quoter) {
        return uzs == null ? null : uzs.divide(quoter.customerRate(), 2, RoundingMode.HALF_UP);
    }

    private static BusinessException blocked() {
        return new BusinessException(HttpStatus.UNPROCESSABLE_CONTENT, "PRODUCT_BLOCKED", "error.catalog.productBlocked");
    }
}
