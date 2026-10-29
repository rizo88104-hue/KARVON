package uz.karvon.wallet.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import org.hibernate.annotations.Immutable;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * One ledger row. Positive amounts are money in, negative money out. Never changed after it is written.
 */
@Getter
@Entity
@Immutable
@Table(name = "wallet_transactions")
public class WalletTransaction {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "wallet_id", nullable = false)
    private UUID walletId;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Enumerated(EnumType.STRING)
    @Column(name = "type", nullable = false, length = 20)
    private WalletTransactionType type;

    @Column(name = "amount_uzs", nullable = false)
    private BigDecimal amountUzs;

    @Column(name = "balance_after_uzs", nullable = false)
    private BigDecimal balanceAfterUzs;

    @Column(name = "order_id")
    private UUID orderId;

    /** Null when the system made it. */
    @Column(name = "created_by")
    private UUID createdBy;

    @Column(name = "comment", length = 500)
    private String comment;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    protected WalletTransaction() {
    }

    public WalletTransaction(UUID walletId, UUID userId, WalletTransactionType type, BigDecimal amountUzs,
                             BigDecimal balanceAfterUzs, UUID orderId, UUID createdBy, String comment, Instant createdAt) {
        this.walletId = walletId;
        this.userId = userId;
        this.type = type;
        this.amountUzs = amountUzs;
        this.balanceAfterUzs = balanceAfterUzs;
        this.orderId = orderId;
        this.createdBy = createdBy;
        this.comment = comment;
        this.createdAt = createdAt;
    }
}
