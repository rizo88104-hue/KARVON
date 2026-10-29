package uz.karvon.order.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;
import uz.karvon.common.domain.BaseEntity;

import java.math.BigDecimal;

/**
 * A copy of the cart line and its price at order time, plus what the buyer fills in while purchasing.
 */
@Getter
@Setter
@Entity
@Table(name = "order_items")
public class OrderItem extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "order_id", nullable = false, updatable = false)
    private Order order;

    @Column(name = "external_product_id", nullable = false, updatable = false, length = 20)
    private String externalProductId;

    @Column(name = "sku_id", nullable = false, updatable = false, length = 40)
    private String skuId;

    @Column(name = "title", nullable = false, length = 500)
    private String title;

    @Column(name = "image_url", length = 1000)
    private String imageUrl;

    /** JSON array of translated SKU properties. */
    @Column(name = "sku_properties", nullable = false)
    private String skuProperties;

    @Column(name = "shop_name", length = 200)
    private String shopName;

    @Column(name = "price_cny", nullable = false)
    private BigDecimal priceCny;

    @Column(name = "china_shipping_cny", nullable = false)
    private BigDecimal chinaShippingCny;

    @Column(name = "weight_kg")
    private BigDecimal weightKg;

    @Column(name = "quantity", nullable = false)
    private int quantity;

    @Column(name = "comment", length = 300)
    private String comment;

    @Column(name = "goods_uzs", nullable = false)
    private BigDecimal goodsUzs;

    @Column(name = "service_fee_uzs", nullable = false)
    private BigDecimal serviceFeeUzs;

    @Column(name = "shipping_uzs", nullable = false)
    private BigDecimal shippingUzs;

    @Column(name = "total_uzs", nullable = false)
    private BigDecimal totalUzs;

    @Column(name = "chargeable_weight_kg", nullable = false)
    private BigDecimal chargeableWeightKg;

    @Column(name = "actual_price_cny")
    private BigDecimal actualPriceCny;

    @Column(name = "taobao_order_number", length = 100)
    private String taobaoOrderNumber;

    @Column(name = "china_tracking_number", length = 100)
    private String chinaTrackingNumber;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private OrderItemStatus status = OrderItemStatus.OK;
}
