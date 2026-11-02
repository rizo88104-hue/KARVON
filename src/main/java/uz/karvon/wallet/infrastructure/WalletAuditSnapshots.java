package uz.karvon.wallet.infrastructure;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import uz.karvon.common.application.AuditSnapshotSource;
import uz.karvon.wallet.application.WalletService;

import java.math.BigDecimal;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * The wallet balance before a manual change, for the audit log (the id is the user id).
 */
@Configuration
public class WalletAuditSnapshots {

    @Bean
    AuditSnapshotSource walletSnapshots(WalletService wallets) {
        return AuditSnapshotSource.of("WALLET", id -> {
            if (id == null) {
                return Optional.empty();
            }
            BigDecimal balance = wallets.balance(UUID.fromString(id));
            return Optional.of(Map.of("userId", id, "balanceUzs", balance));
        });
    }
}
