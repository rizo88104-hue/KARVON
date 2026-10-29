package uz.karvon.order.domain;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;
import uz.karvon.common.domain.BaseEntity;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * An order of a customer. The status changes only through the order service, which asks {@link OrderStateMachine}.
 * {@code totalUzs} is what was charged at checkout; a positive {@code shippingDifferenceUzs} is charged on top.
 */
@Getter
@Setter
@Entity
@Table(name = "orders")
public class Order extends BaseEntity {

    @Column(name = "number", nullable = false, unique = true, updatable = false, length = 20)
    private String number;

    @Column(name = "customer_id", nullable = false, updatable = false)
    private UUID customerId;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 30)
    private OrderStatus status;

    @Enumerated(EnumType.STRING)
    @Column(name = "delivery_method", nullable = false, length = 20)
    private DeliveryMethod deliveryMethod;

    /** JSON copy of the address (AddressResponse); null for cargo pickup without an address. */
    @Column(name = "address_snapshot")
    private String addressSnapshot;

    @Enumerated(EnumType.STRING)
    @Column(name = "payment_method", nullable = false, length = 20)
    private PaymentMethod paymentMethod;

    @Column(name = "pricing_settings_id", nullable = false, updatable = false)
    private UUID pricingSettingsId;

    @Column(name = "goods_uzs", nullable = false)
    private BigDecimal goodsUzs;

    @Column(name = "service_fee_uzs", nullable = false)
    private BigDecimal serviceFeeUzs;

    @Column(name = "prepaid_shipping_uzs", nullable = false)
    private BigDecimal prepaidShippingUzs;

    @Column(name = "real_weight_kg")
    private BigDecimal realWeightKg;

    @Column(name = "final_shipping_uzs")
    private BigDecimal finalShippingUzs;

    @Column(name = "shipping_difference_uzs", nullable = false)
    private BigDecimal shippingDifferenceUzs = BigDecimal.ZERO;

    @Column(name = "total_uzs", nullable = false)
    private BigDecimal totalUzs;

    @Column(name = "paid_uzs", nullable = false)
    private BigDecimal paidUzs = BigDecimal.ZERO;

    @Column(name = "refunded_uzs", nullable = false)
    private BigDecimal refundedUzs = BigDecimal.ZERO;

    @Column(name = "customer_comment", length = 500)
    private String customerComment;

    @Column(name = "international_tracking_number", length = 100)
    private String internationalTrackingNumber;

    @Column(name = "paid_at")
    private Instant paidAt;

    @Column(name = "delivered_at")
    private Instant deliveredAt;

    @OneToMany(mappedBy = "order", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("createdAt ASC")
    private List<OrderItem> items = new ArrayList<>();

    protected Order() {
    }

    public Order(String number, UUID customerId) {
        this.number = number;
        this.customerId = customerId;
        this.status = OrderStatus.AWAITING_PAYMENT;
    }

    public void addItem(OrderItem item) {
        item.setOrder(this);
        items.add(item);
    }

    /** Money still to pay: the order total plus a positive shipping difference, minus what was paid. */
    public BigDecimal amountDueUzs() {
        BigDecimal due = totalUzs.add(shippingDifferenceUzs.max(BigDecimal.ZERO)).subtract(paidUzs);
        return due.max(BigDecimal.ZERO);
    }

    /** Paid and not yet given back. */
    public BigDecimal refundableUzs() {
        return paidUzs.subtract(refundedUzs).max(BigDecimal.ZERO);
    }
}
