package uz.karvon.payment.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;
import uz.karvon.common.domain.BaseEntity;
import uz.karvon.order.application.PaymentPurpose;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * One payment attempt for an order or its shipping difference. {@code number} is our id for the provider
 * (Payme "transaction", Click merchant_prepare_id and merchant_confirm_id).
 */
@Getter
@Setter
@Entity
@Table(name = "payment_transactions")
public class PaymentTransaction extends BaseEntity {

    @Column(name = "number", nullable = false, unique = true, updatable = false)
    private long number;

    @Enumerated(EnumType.STRING)
    @Column(name = "purpose", nullable = false, updatable = false, length = 30)
    private PaymentPurpose purpose;

    @Column(name = "order_id", nullable = false, updatable = false)
    private UUID orderId;

    @Column(name = "order_number", nullable = false, updatable = false, length = 20)
    private String orderNumber;

    @Column(name = "customer_id", nullable = false, updatable = false)
    private UUID customerId;

    @Enumerated(EnumType.STRING)
    @Column(name = "provider", nullable = false, updatable = false, length = 20)
    private PaymentProvider provider;

    /** Payme transaction id, Click click_trans_id. */
    @Column(name = "provider_transaction_id", updatable = false, length = 64)
    private String providerTransactionId;

    @Column(name = "amount_uzs", nullable = false, updatable = false)
    private BigDecimal amountUzs;

    @Enumerated(EnumType.STRING)
    @Column(name = "state", nullable = false, length = 30)
    private PaymentState state = PaymentState.CREATED;

    /** Payme "time": when the transaction was created in Payme Business, epoch ms. */
    @Column(name = "provider_time", updatable = false)
    private Long providerTime;

    @Column(name = "create_time", nullable = false, updatable = false)
    private Instant createTime;

    @Column(name = "perform_time")
    private Instant performTime;

    @Column(name = "cancel_time")
    private Instant cancelTime;

    /** Payme cancel reason code (1-5, 10). */
    @Column(name = "reason")
    private Integer reason;

    protected PaymentTransaction() {
    }

    public PaymentTransaction(long number, PaymentPurpose purpose, UUID orderId, String orderNumber, UUID customerId,
                              PaymentProvider provider, String providerTransactionId, BigDecimal amountUzs,
                              Long providerTime, Instant createTime) {
        this.number = number;
        this.purpose = purpose;
        this.orderId = orderId;
        this.orderNumber = orderNumber;
        this.customerId = customerId;
        this.provider = provider;
        this.providerTransactionId = providerTransactionId;
        this.amountUzs = amountUzs;
        this.providerTime = providerTime;
        this.createTime = createTime;
    }

    public PaymentAccount account() {
        return new PaymentAccount(orderNumber, purpose);
    }

    public void perform(Instant now) {
        state = PaymentState.PERFORMED;
        performTime = now;
    }

    public void cancel(Instant now, Integer cancelReason) {
        state = state == PaymentState.PERFORMED ? PaymentState.CANCELLED_AFTER_PERFORM : PaymentState.CANCELLED;
        cancelTime = now;
        reason = cancelReason;
    }
}
