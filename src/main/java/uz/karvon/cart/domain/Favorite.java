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
 * A product the customer saved to "Sevimlilar", with its summary as it was when saved (title in Chinese, photo,
 * CNY prices, shop). The list shows it in the customer's language with the current UZS price.
 */
@Getter
@Setter
@Entity
@Table(name = "favorites")
public class Favorite extends BaseEntity {

    /** Most favorites one customer may keep. */
    public static final int MAX_PER_USER = 200;

    @Column(name = "user_id", nullable = false, updatable = false)
    private UUID userId;

    @Column(name = "external_product_id", nullable = false, updatable = false, length = 20)
    private String externalProductId;

    @Column(name = "title_original", nullable = false, length = 500)
    private String titleOriginal;

    @Column(name = "image_url", length = 1000)
    private String imageUrl;

    @Column(name = "price_cny", nullable = false, precision = 12, scale = 2)
    private BigDecimal priceCny;

    @Column(name = "original_price_cny", precision = 12, scale = 2)
    private BigDecimal originalPriceCny;

    @Column(name = "sales_count", nullable = false)
    private long salesCount;

    @Column(name = "shop_name", length = 200)
    private String shopName;
}
