package uz.karvon.wallet.api.dto;

import uz.karvon.common.api.PageResponse;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * Balance and history, newest first.
 */
public record WalletResponse(UUID userId, BigDecimal balanceUzs, PageResponse<WalletTransactionResponse> transactions) {
}
