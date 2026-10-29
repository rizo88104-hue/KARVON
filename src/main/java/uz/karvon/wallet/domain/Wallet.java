package uz.karvon.wallet.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;
import uz.karvon.common.domain.BaseEntity;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * The money a user holds with us. Changed only by WalletService, which locks the row and writes a ledger entry
 * for every change, so the balance always equals the sum of the ledger.
 */
@Getter
@Setter
@Entity
@Table(name = "wallets")
public class Wallet extends BaseEntity {

    @Column(name = "user_id", nullable = false, unique = true, updatable = false)
    private UUID userId;

    @Column(name = "balance_uzs", nullable = false)
    private BigDecimal balanceUzs = BigDecimal.ZERO;

    protected Wallet() {
    }
}
