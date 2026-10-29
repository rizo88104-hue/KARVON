package uz.karvon.cart.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;
import uz.karvon.common.domain.BaseEntity;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * One line of a customer's cart: a product SKU with the quantity, a comment for our buyer in China and the
 * product data as it was when added (or at the last price refresh).
 */
@Getter
@Setter
@Entity
@Table(name = "cart_items")
public class CartItem extends BaseEntity {

    public static final int MAX_QUANTITY = 99;

    @Column(name = "user_id", nullable = false, updatable = false)
    private UUID userId;

    @Column(name = "external_product_id", nullable = false, updatable = false, length = 20)
    private String externalProductId;

    @Column(name = "sku_id", nullable = false, updatable = false, length = 40)
    private String skuId;

    @Column(name = "title_snapshot", nullable = false, length = 500)
    private String titleSnapshot;

    @Column(name = "image_snapshot", length = 1000)
    private String imageSnapshot;

    /** JSON array of translated SKU properties (see TranslatedPair). */
    @Column(name = "sku_properties_snapshot", nullable = false)
    private String skuPropertiesSnapshot;

    @Column(name = "shop_name", length = 200)
    private String shopName;

    @Column(name = "price_cny_snapshot", nullable = false)
    private BigDecimal priceCnySnapshot;

    @Column(name = "china_shipping_cny", nullable = false)
    private BigDecimal chinaShippingCny;

    /** Null when Taobao gives no weight; pricing then uses the default weight. */
    @Column(name = "weight_kg")
    private BigDecimal weightKg;

    @Column(name = "quantity", nullable = false)
    private int quantity;

    @Column(name = "comment", length = 300)
    private String comment;

    @Column(name = "selected", nullable = false)
    private boolean selected = true;

    /** The price before the last refresh changed it; null when it did not change. */
    @Column(name = "previous_price_cny")
    private BigDecimal previousPriceCny;

    @Column(name = "out_of_stock", nullable = false)
    private boolean outOfStock;

    @Column(name = "blocked", nullable = false)
    private boolean blocked;

    protected CartItem() {
    }

    public CartItem(UUID userId, String externalProductId, String skuId) {
        this.userId = userId;
        this.externalProductId = externalProductId;
        this.skuId = skuId;
    }

    /** Can be ordered now: in stock and not blocked. Only such selected lines count in the totals. */
    public boolean isAvailable() {
        return !outOfStock && !blocked;
    }

    public boolean isPriceChanged() {
        return previousPriceCny != null;
    }
}
