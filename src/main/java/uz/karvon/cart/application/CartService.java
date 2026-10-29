package uz.karvon.cart.application;

import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.json.JsonMapper;
import uz.karvon.cart.api.dto.AddCartItemRequest;
import uz.karvon.cart.api.dto.CartItemResponse;
import uz.karvon.cart.api.dto.CartResponse;
import uz.karvon.cart.api.dto.CartShopResponse;
import uz.karvon.cart.api.dto.UpdateCartItemRequest;
import uz.karvon.cart.domain.CartItem;
import uz.karvon.cart.domain.CartTotals;
import uz.karvon.cart.infrastructure.CartItemRepository;
import uz.karvon.catalog.application.CatalogService;
import uz.karvon.catalog.application.ProductBlockedException;
import uz.karvon.catalog.application.ProductNotFoundException;
import uz.karvon.catalog.domain.ProductDetail;
import uz.karvon.catalog.domain.Sku;
import uz.karvon.catalog.domain.TranslatedPair;
import uz.karvon.common.application.BusinessException;
import uz.karvon.common.application.NotFoundException;
import uz.karvon.pricing.application.PriceQuoter;
import uz.karvon.pricing.application.PricingService;
import uz.karvon.pricing.domain.PriceBreakdown;
import uz.karvon.translation.domain.Lang;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * The customer's cart. Products are read through the catalog (so blocked goods are refused) and every answer
 * is priced with the current pricing settings. Totals count only selected lines that are in stock and not blocked.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class CartService {

    public static final int MAX_ITEMS = 50;

    private static final TypeReference<List<TranslatedPair>> PROPERTIES = new TypeReference<>() {
    };

    private final CartItemRepository items;
    private final CatalogService catalog;
    private final PricingService pricing;
    private final JsonMapper json;

    public CartResponse get(UUID userId) {
        return toResponse(items.findByUserIdOrderByCreatedAtAsc(userId));
    }

    /**
     * Adds a SKU, or adds to its quantity if it is already in the cart. The product must exist, not be blocked,
     * and have enough stock for the new quantity.
     */
    @Transactional
    public CartResponse add(UUID userId, AddCartItemRequest request, Lang lang) {
        int quantity = request.quantity() == null ? 1 : request.quantity();
        ProductDetail product = catalog.orderableProduct(request.externalProductId(), lang, false);
        Sku sku = findSku(product, request.skuId())
                .orElseThrow(() -> new NotFoundException("SKU_NOT_FOUND", "error.cart.skuNotFound"));

        Optional<CartItem> existing = items.findByUserIdAndExternalProductIdAndSkuId(userId, product.externalId(), sku.skuId());
        int newQuantity = existing.map(item -> item.getQuantity() + quantity).orElse(quantity);
        if (newQuantity > CartItem.MAX_QUANTITY) {
            throw new BusinessException("CART_LIMIT", "error.cart.quantityLimit", CartItem.MAX_QUANTITY);
        }
        if (existing.isEmpty() && items.countByUserId(userId) >= MAX_ITEMS) {
            throw new BusinessException("CART_LIMIT", "error.cart.itemLimit", MAX_ITEMS);
        }
        if (sku.stock() <= 0) {
            throw new BusinessException(HttpStatus.CONFLICT, "OUT_OF_STOCK", "error.cart.outOfStock");
        }
        if (sku.stock() < newQuantity) {
            throw new BusinessException(HttpStatus.CONFLICT, "OUT_OF_STOCK", "error.cart.notEnoughStock", sku.stock());
        }

        CartItem item = existing.orElseGet(() -> new CartItem(userId, product.externalId(), sku.skuId()));
        takeSnapshot(item, product, sku);
        item.setPreviousPriceCny(null);
        item.setOutOfStock(false);
        item.setBlocked(false);
        item.setQuantity(newQuantity);
        item.setSelected(true);
        if (request.comment() != null) {
            item.setComment(blankToNull(request.comment()));
        }
        items.save(item);
        return get(userId);
    }

    @Transactional
    public CartResponse update(UUID userId, UUID itemId, UpdateCartItemRequest request) {
        CartItem item = load(userId, itemId);
        if (request.quantity() != null) {
            item.setQuantity(request.quantity());
        }
        if (request.comment() != null) {
            item.setComment(blankToNull(request.comment()));
        }
        if (request.selected() != null) {
            item.setSelected(request.selected());
        }
        return get(userId);
    }

    @Transactional
    public CartResponse selectAll(UUID userId, boolean selected) {
        items.findByUserIdOrderByCreatedAtAsc(userId).forEach(item -> item.setSelected(selected));
        return get(userId);
    }

    @Transactional
    public CartResponse delete(UUID userId, UUID itemId) {
        items.delete(load(userId, itemId));
        items.flush();
        return get(userId);
    }

    /** Deletes the lines whose "selected" flag equals {@code selected}, e.g. after they were ordered. */
    @Transactional
    public CartResponse deleteBySelected(UUID userId, boolean selected) {
        items.deleteAll(items.findByUserIdAndSelected(userId, selected));
        items.flush();
        return get(userId);
    }

    /**
     * Reads every product again from the provider (not from the cache). A changed price is taken over and the
     * old one kept in {@code previousPriceCny}; lines without enough stock, removed or blocked are marked and
     * unselected. A provider outage fails the whole refresh (CATALOG_UNAVAILABLE) and changes nothing.
     */
    @Transactional
    public CartResponse refreshPrices(UUID userId, Lang lang) {
        Map<String, List<CartItem>> byProduct = items.findByUserIdOrderByCreatedAtAsc(userId).stream()
                .collect(Collectors.groupingBy(CartItem::getExternalProductId, LinkedHashMap::new, Collectors.toList()));
        for (Map.Entry<String, List<CartItem>> entry : byProduct.entrySet()) {
            ProductDetail product;
            try {
                product = catalog.orderableProduct(entry.getKey(), lang, true);
            } catch (ProductBlockedException ex) {
                entry.getValue().forEach(item -> markUnavailable(item, false, true));
                continue;
            } catch (ProductNotFoundException ex) {
                entry.getValue().forEach(item -> markUnavailable(item, true, false));
                continue;
            }
            for (CartItem item : entry.getValue()) {
                Optional<Sku> sku = findSku(product, item.getSkuId());
                if (sku.isEmpty()) {
                    markUnavailable(item, true, false);
                    continue;
                }
                boolean priceChanged = sku.get().priceCny().compareTo(item.getPriceCnySnapshot()) != 0;
                item.setPreviousPriceCny(priceChanged ? item.getPriceCnySnapshot() : null);
                takeSnapshot(item, product, sku.get());
                item.setBlocked(false);
                item.setOutOfStock(sku.get().stock() < item.getQuantity());
                if (item.isOutOfStock()) {
                    item.setSelected(false);
                }
            }
        }
        return get(userId);
    }

    private CartResponse toResponse(List<CartItem> cart) {
        PriceQuoter quoter = pricing.quoter();
        Map<String, List<CartItemResponse>> shopItems = new LinkedHashMap<>();
        Map<String, CartTotals> shopTotals = new LinkedHashMap<>();
        CartTotals totals = CartTotals.EMPTY;
        int selectedCount = 0;
        for (CartItem item : cart) {
            PriceBreakdown price = quoter.price(item.getPriceCnySnapshot(), item.getChinaShippingCny(),
                    item.getQuantity(), item.getWeightKg());
            String shop = Objects.toString(item.getShopName(), "");
            shopItems.computeIfAbsent(shop, key -> new ArrayList<>()).add(toResponse(item, price));
            shopTotals.putIfAbsent(shop, CartTotals.EMPTY);
            if (item.isSelected()) {
                selectedCount++;
            }
            if (item.isSelected() && item.isAvailable()) {
                totals = totals.plus(price, item.getQuantity());
                shopTotals.computeIfPresent(shop, (key, sum) -> sum.plus(price, item.getQuantity()));
            }
        }
        List<CartShopResponse> shops = shopItems.entrySet().stream()
                .map(entry -> new CartShopResponse(entry.getKey(), entry.getValue(), shopTotals.get(entry.getKey())))
                .toList();
        return new CartResponse(shops, totals, cart.size(), selectedCount, quoter.settingsId());
    }

    private CartItemResponse toResponse(CartItem item, PriceBreakdown price) {
        return new CartItemResponse(item.getId(), item.getExternalProductId(), item.getSkuId(), item.getTitleSnapshot(),
                item.getImageSnapshot(), json.readValue(item.getSkuPropertiesSnapshot(), PROPERTIES), item.getShopName(),
                item.getPriceCnySnapshot(), item.getChinaShippingCny(), item.getWeightKg(), item.getQuantity(),
                item.getComment(), item.isSelected(), item.isPriceChanged(), item.getPreviousPriceCny(),
                item.isOutOfStock(), item.isBlocked(), price);
    }

    private void takeSnapshot(CartItem item, ProductDetail product, Sku sku) {
        item.setTitleSnapshot(truncate(product.titleTranslated() != null ? product.titleTranslated() : product.titleOriginal(), 500));
        item.setImageSnapshot(truncate(sku.imageUrl() != null ? sku.imageUrl() : product.mainImageUrl(), 1000));
        item.setSkuPropertiesSnapshot(json.writeValueAsString(sku.properties()));
        item.setShopName(product.shop() == null ? null : truncate(product.shop().name(), 200));
        item.setPriceCnySnapshot(sku.priceCny());
        item.setChinaShippingCny(product.chinaShippingCny() == null ? BigDecimal.ZERO : product.chinaShippingCny());
        item.setWeightKg(product.weightKg());
    }

    private static void markUnavailable(CartItem item, boolean outOfStock, boolean blocked) {
        item.setOutOfStock(outOfStock);
        item.setBlocked(blocked);
        item.setPreviousPriceCny(null);
        item.setSelected(false);
    }

    private static Optional<Sku> findSku(ProductDetail product, String skuId) {
        return product.skus().stream().filter(sku -> sku.skuId().equals(skuId)).findFirst();
    }

    private CartItem load(UUID userId, UUID itemId) {
        return items.findByIdAndUserId(itemId, userId)
                .orElseThrow(() -> new NotFoundException("CART_ITEM_NOT_FOUND", "error.cart.itemNotFound"));
    }

    private static String blankToNull(String text) {
        String trimmed = text.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private static String truncate(String text, int max) {
        return text == null || text.length() <= max ? text : text.substring(0, max);
    }
}
