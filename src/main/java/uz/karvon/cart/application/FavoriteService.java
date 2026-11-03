package uz.karvon.cart.application;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import uz.karvon.cart.domain.Favorite;
import uz.karvon.cart.infrastructure.FavoriteRepository;
import uz.karvon.catalog.api.dto.ProductCardResponse;
import uz.karvon.catalog.application.CatalogService;
import uz.karvon.catalog.domain.ProductDetail;
import uz.karvon.catalog.domain.ProductSummary;
import uz.karvon.common.application.BusinessException;
import uz.karvon.translation.domain.Lang;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.UUID;

/**
 * "Sevimlilar": products a customer saved for later. Saving checks the product through the catalog (it must exist
 * and not be blocked) and keeps its summary; the list is shown as product cards in the customer's language with the
 * current UZS price, newest first. Blocked goods (e.g. a keyword blocked later) are left out of the list.
 */
@Service
@RequiredArgsConstructor
public class FavoriteService {

    private final FavoriteRepository favorites;
    private final CatalogService catalog;

    /** Not in a read-only transaction: translating the titles may write new translations to the cache table. */
    public List<ProductCardResponse> list(UUID userId, Lang lang) {
        List<ProductSummary> items = favorites.findByUserIdOrderByCreatedAtDesc(userId).stream()
                .map(f -> new ProductSummary(f.getExternalProductId(), f.getTitleOriginal(), null, f.getImageUrl(),
                        plain(f.getPriceCny()), f.getSalesCount(), f.getShopName(), plain(f.getOriginalPriceCny())))
                .toList();
        return catalog.cards(items, lang);
    }

    /** 129.00 from the database as 129, like the prices the catalog answers. */
    private static BigDecimal plain(BigDecimal cny) {
        return cny == null ? null : cny.stripTrailingZeros();
    }

    /** Ids of the saved products, newest first (for the heart buttons). */
    @Transactional(readOnly = true)
    public List<String> ids(UUID userId) {
        return favorites.findProductIds(userId);
    }

    /** Saving a product that is already saved changes nothing. */
    @Transactional
    public void add(UUID userId, String externalId, Lang lang) {
        if (favorites.existsByUserIdAndExternalProductId(userId, externalId)) {
            return;
        }
        if (favorites.countByUserId(userId) >= Favorite.MAX_PER_USER) {
            throw new BusinessException("FAVORITES_LIMIT", "error.favorite.limit", Favorite.MAX_PER_USER);
        }
        save(userId, catalog.orderableProduct(externalId, lang, false));
    }

    /** Removing a product that is not saved changes nothing. */
    @Transactional
    public void remove(UUID userId, String externalId) {
        favorites.deleteByUserIdAndProduct(userId, externalId);
    }

    /**
     * Saves the favorites a guest collected in the browser, given newest first, after logging in. Products that
     * cannot be saved (unknown, blocked, catalog unavailable, over the limit) are skipped. Answers all saved ids.
     */
    @Transactional
    public List<String> merge(UUID userId, List<String> externalIds, Lang lang) {
        List<String> wanted = new ArrayList<>(new LinkedHashSet<>(externalIds));
        // Oldest first, so the newest guest favorite also ends up newest in the list.
        for (String externalId : wanted.reversed()) {
            if (favorites.existsByUserIdAndExternalProductId(userId, externalId)) {
                continue;
            }
            if (favorites.countByUserId(userId) >= Favorite.MAX_PER_USER) {
                break;
            }
            try {
                save(userId, catalog.orderableProduct(externalId, lang, false));
            } catch (BusinessException skipped) {
                // unknown or blocked product, or the catalog is down: the browser keeps it and tries again later
            }
        }
        return ids(userId);
    }

    private void save(UUID userId, ProductDetail product) {
        Favorite favorite = new Favorite();
        favorite.setUserId(userId);
        favorite.setExternalProductId(product.externalId());
        favorite.setTitleOriginal(product.titleOriginal());
        favorite.setImageUrl(product.mainImageUrl());
        favorite.setPriceCny(product.priceCny());
        favorite.setOriginalPriceCny(product.originalPriceCny());
        favorite.setSalesCount(product.salesCount());
        favorite.setShopName(product.shop() == null ? null : product.shop().name());
        favorites.saveAndFlush(favorite);
    }
}
