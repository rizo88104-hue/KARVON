package uz.karvon.catalog.infrastructure;

import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;
import uz.karvon.catalog.application.ProductNotFoundException;
import uz.karvon.catalog.domain.ProductDetail;
import uz.karvon.catalog.domain.ProductSummary;
import uz.karvon.catalog.domain.SearchResult;
import uz.karvon.catalog.domain.ShopInfo;
import uz.karvon.catalog.domain.Sku;
import uz.karvon.catalog.domain.SortOrder;
import uz.karvon.catalog.domain.TranslatedPair;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * ALL knowledge of the third-party Taobao API's JSON lives here, so changing the provider means changing this
 * class only. It reads the "item_search" / "item_get" format used by most Taobao API resellers:
 * <pre>
 * item_search: {"items":{"page":1,"page_size":40,"real_total_results":1234,
 *               "item":[{"num_iid":"...","title":"...","pic_url":"//img...","price":"129.00",
 *                        "promotion_price":"99.00","sales":1523,"seller_nick":"..."}]},"error_code":"0000"}
 * item_get:    {"item":{"num_iid":"...","title":"...","price":"129.00","pic_url":"...","item_imgs":[{"url":"..."}],
 *               "props":[{"name":"品牌","value":"..."}],"nick":"...","shop_id":"...","post_fee":"0.00",
 *               "item_weight":"0.9","props_img":{"1627207:28341":"//img..."},
 *               "skus":{"sku":[{"sku_id":"...","price":"129.00","quantity":"120","properties":"1627207:28341",
 *                               "properties_name":"1627207:28341:颜色分类:黑色;20509:28314:尺码:S"}]}},
 *               "error_code":"0000"}
 * </pre>
 * Unexpected JSON throws {@link IllegalStateException}; a missing item throws {@link ProductNotFoundException}.
 */
public class TaobaoResponseMapper {

    private static final Set<String> OK_CODES = Set.of("", "0", "0000", "200");
    private static final BigDecimal MAX_REASONABLE_WEIGHT_KG = new BigDecimal("500");

    private final JsonMapper json;

    public TaobaoResponseMapper(JsonMapper json) {
        this.json = json;
    }

    /** The "sort" request parameter for our sort order. */
    public String sortParam(SortOrder sort) {
        return switch (sort) {
            case RELEVANCE -> "";
            case PRICE_ASC -> "bid";
            case PRICE_DESC -> "_bid";
            case SALES -> "_sale";
        };
    }

    /** {@code page} is our zero-based page; the API counts from 1. */
    public int pageParam(int page) {
        return page + 1;
    }

    public SearchResult toSearchResult(String body, int page) {
        JsonNode root = parse(body);
        checkError(root, false);
        JsonNode items = root.path("items");
        if (items.isMissingNode()) {
            throw new IllegalStateException("Taobao search answer has no \"items\"");
        }
        List<ProductSummary> products = new ArrayList<>();
        for (JsonNode item : items.path("item")) {
            String id = text(item, "num_iid");
            if (id == null) {
                continue;
            }
            products.add(new ProductSummary(id, text(item, "title"), null, image(text(item, "pic_url")),
                    price(item), number(item, "sales", 0L), firstText(item, "seller_nick", "nick", "shop_title")));
        }
        int pageSize = (int) number(items, "page_size", products.size());
        long total = number(items, "real_total_results", number(items, "total_results", products.size()));
        return new SearchResult(products, page, pageSize, total);
    }

    public ProductDetail toProductDetail(String body) {
        JsonNode root = parse(body);
        checkError(root, true);
        JsonNode item = root.path("item");
        String id = text(item, "num_iid");
        if (id == null) {
            throw new ProductNotFoundException();
        }
        List<String> images = new ArrayList<>();
        for (JsonNode image : item.path("item_imgs")) {
            String url = image(text(image, "url"));
            if (url != null && !images.contains(url)) {
                images.add(url);
            }
        }
        String mainImage = image(text(item, "pic_url"));
        if (mainImage == null && !images.isEmpty()) {
            mainImage = images.getFirst();
        }
        List<TranslatedPair> attributes = new ArrayList<>();
        for (JsonNode prop : item.path("props")) {
            String name = text(prop, "name");
            String value = text(prop, "value");
            if (name != null && value != null) {
                attributes.add(TranslatedPair.original(name, value));
            }
        }
        BigDecimal itemPrice = price(item);
        List<Sku> skus = skus(item, id, itemPrice);
        BigDecimal lowest = skus.stream().map(Sku::priceCny).min(Comparator.naturalOrder()).orElse(itemPrice);
        return new ProductDetail(id, text(item, "title"), null, mainImage, images, lowest,
                number(item, "total_sold", number(item, "sales", 0L)),
                new ShopInfo(firstText(item, "shop_id", "seller_id"), firstText(item, "nick", "seller_nick", "shop_title")),
                attributes, skus, decimal(item, "post_fee", decimal(item, "express_fee", BigDecimal.ZERO)), weight(item));
    }

    private List<Sku> skus(JsonNode item, String itemId, BigDecimal itemPrice) {
        JsonNode images = item.path("props_img");
        List<Sku> skus = new ArrayList<>();
        for (JsonNode sku : item.path("skus").path("sku")) {
            List<TranslatedPair> properties = new ArrayList<>();
            String names = text(sku, "properties_name");
            if (names != null) {
                // "pid:vid:name:value;pid:vid:name:value"
                for (String part : names.split(";")) {
                    String[] fields = part.split(":", 4);
                    if (fields.length == 4) {
                        properties.add(TranslatedPair.original(fields[2], fields[3]));
                    }
                }
            }
            String imageUrl = null;
            String keys = text(sku, "properties");
            if (keys != null) {
                for (String key : keys.split(";")) {
                    imageUrl = image(text(images, key));
                    if (imageUrl != null) {
                        break;
                    }
                }
            }
            String skuId = text(sku, "sku_id");
            BigDecimal skuPrice = decimal(sku, "price", itemPrice);
            if (skuId != null && skuPrice != null) {
                skus.add(new Sku(skuId, properties, skuPrice, (int) number(sku, "quantity", 0L), imageUrl));
            }
        }
        if (skus.isEmpty()) {
            // A product without variants is sold as one SKU with the item id.
            skus.add(new Sku(itemId, List.of(), itemPrice, (int) number(item, "num", 0L), null));
        }
        return skus;
    }

    private void checkError(JsonNode root, boolean productRequest) {
        String code = firstText(root, "error_code", "code");
        if (code == null || OK_CODES.contains(code)) {
            return;
        }
        String error = (code + " " + firstText(root, "error", "reason", "message")).toLowerCase(Locale.ROOT);
        if (productRequest && (error.contains("not-found") || error.contains("not found") || error.contains("不存在")
                || error.contains("下架") || code.equals("2000"))) {
            throw new ProductNotFoundException();
        }
        throw new IllegalStateException("Taobao API error: " + error);
    }

    private JsonNode parse(String body) {
        if (body == null || body.isBlank()) {
            throw new IllegalStateException("Empty answer from the Taobao API");
        }
        try {
            return json.readTree(body);
        } catch (JacksonException ex) {
            throw new IllegalStateException("The Taobao API did not answer JSON", ex);
        }
    }

    /** The promotion price when there is one, otherwise the normal price. */
    private static BigDecimal price(JsonNode item) {
        BigDecimal promotion = decimal(item, "promotion_price", null);
        BigDecimal normal = decimal(item, "price", null);
        if (promotion != null && promotion.signum() > 0) {
            return promotion;
        }
        if (normal == null) {
            throw new IllegalStateException("Taobao item without a price");
        }
        return normal;
    }

    private static BigDecimal weight(JsonNode item) {
        BigDecimal weight = decimal(item, "item_weight", decimal(item, "weight", null));
        return weight == null || weight.signum() <= 0 || weight.compareTo(MAX_REASONABLE_WEIGHT_KG) > 0 ? null : weight;
    }

    private static String image(String url) {
        if (url == null || url.isBlank()) {
            return null;
        }
        return url.startsWith("//") ? "https:" + url : url;
    }

    private static String text(JsonNode node, String field) {
        JsonNode value = node.path(field);
        if (value.isMissingNode() || value.isNull()) {
            return null;
        }
        String text = value.asString().trim();
        return text.isEmpty() ? null : text;
    }

    private static String firstText(JsonNode node, String... fields) {
        for (String field : fields) {
            String value = text(node, field);
            if (value != null) {
                return value;
            }
        }
        return null;
    }

    private static BigDecimal decimal(JsonNode node, String field, BigDecimal fallback) {
        String value = text(node, field);
        if (value == null) {
            return fallback;
        }
        try {
            return new BigDecimal(value);
        } catch (NumberFormatException ex) {
            return fallback;
        }
    }

    private static long number(JsonNode node, String field, long fallback) {
        BigDecimal value = decimal(node, field, null);
        return value == null ? fallback : value.longValue();
    }
}
