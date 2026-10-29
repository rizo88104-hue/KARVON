package uz.karvon.wallet.api.dto;

import uz.karvon.wallet.domain.WalletTransactionType;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * A ledger row; {@code amountUzs} is positive for money in, negative for money out.
 */
public record WalletTransactionResponse(UUID id, WalletTransactionType type, BigDecimal amountUzs,
                                        BigDecimal balanceAfterUzs, UUID orderId, UUID createdBy, String comment,
                                        Instant createdAt) {
}
