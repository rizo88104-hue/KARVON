package uz.karvon.wallet.application;

import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.event.EventListener;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import uz.karvon.common.api.PageResponse;
import uz.karvon.common.application.BusinessException;
import uz.karvon.common.application.NotFoundException;
import uz.karvon.order.application.OrderRefundRequestedEvent;
import uz.karvon.order.application.RefundReason;
import uz.karvon.user.application.UserService;
import uz.karvon.wallet.api.dto.WalletResponse;
import uz.karvon.wallet.api.dto.WalletTransactionResponse;
import uz.karvon.wallet.domain.Wallet;
import uz.karvon.wallet.domain.WalletTransaction;
import uz.karvon.wallet.domain.WalletTransactionType;
import uz.karvon.wallet.infrastructure.WalletRepository;
import uz.karvon.wallet.infrastructure.WalletTransactionRepository;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.util.UUID;

/**
 * Wallet balances. Every change locks the wallet row (SELECT ... FOR UPDATE), checks the new balance and writes
 * one ledger row, so concurrent changes are applied one after another and the balance equals the ledger sum.
 * Runs in the caller's transaction (e.g. with the order or payment change that caused it).
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class WalletService {

    private static final Logger log = LoggerFactory.getLogger(WalletService.class);

    private final WalletRepository wallets;
    private final WalletTransactionRepository ledger;
    private final UserService users;
    private final Clock clock;

    public WalletResponse get(UUID userId, int page, int size) {
        BigDecimal balance = wallets.findByUserId(userId).map(Wallet::getBalanceUzs).orElse(BigDecimal.ZERO);
        return new WalletResponse(userId, balance, PageResponse.of(
                ledger.findByUserIdOrderByCreatedAtDesc(userId, PageRequest.of(page, size)), WalletService::toResponse));
    }

    public BigDecimal balance(UUID userId) {
        return wallets.findByUserId(userId).map(Wallet::getBalanceUzs).orElse(BigDecimal.ZERO);
    }

    /** Adds money. {@code createdBy} is null for the system. */
    @Transactional
    public WalletTransactionResponse credit(UUID userId, BigDecimal amount, WalletTransactionType type, UUID orderId,
                                            UUID createdBy, String comment) {
        requirePositive(amount);
        return apply(userId, amount, type, orderId, createdBy, comment);
    }

    /** Takes money; INSUFFICIENT_FUNDS if the balance is too small. */
    @Transactional
    public WalletTransactionResponse debit(UUID userId, BigDecimal amount, WalletTransactionType type, UUID orderId,
                                           UUID createdBy, String comment) {
        requirePositive(amount);
        return apply(userId, amount.negate(), type, orderId, createdBy, comment);
    }

    /** ADMIN correction, positive or negative, with a reason; kept in the ledger and the log. */
    @Transactional
    public WalletTransactionResponse adjust(UUID adminId, UUID userId, BigDecimal amount, String reason) {
        requireUser(userId);
        if (amount.signum() == 0) {
            throw new BusinessException("WALLET_AMOUNT_ZERO", "error.wallet.amountZero");
        }
        log.info("ADMIN {} adjusts wallet of {} by {} UZS: {}", adminId, userId, amount, reason);
        return apply(userId, amount, WalletTransactionType.ADMIN_ADJUSTMENT, null, adminId, reason.trim());
    }

    /**
     * ADMIN returned money from the wallet to the customer card by hand (e.g. in the Payme or Click cabinet).
     * TODO: automatic card refund through the providers.
     */
    @Transactional
    public WalletTransactionResponse cardRefund(UUID adminId, UUID userId, BigDecimal amount, String comment) {
        requireUser(userId);
        log.info("ADMIN {} marks {} UZS of wallet {} as refunded to card: {}", adminId, amount, userId, comment);
        return debit(userId, amount, WalletTransactionType.CARD_REFUND, null, adminId, comment.trim());
    }

    /** Order refunds go to the wallet, in the same transaction as the order change. */
    @EventListener
    @Transactional
    public void onRefundRequested(OrderRefundRequestedEvent event) {
        WalletTransactionType type = event.reason() == RefundReason.SHIPPING_OVERPAID
                ? WalletTransactionType.SHIPPING_REFUND : WalletTransactionType.REFUND;
        credit(event.customerId(), event.amountUzs(), type, event.orderId(), null, event.number() + ": " + event.reason());
    }

    private WalletTransactionResponse apply(UUID userId, BigDecimal signedAmount, WalletTransactionType type, UUID orderId,
                                            UUID createdBy, String comment) {
        wallets.createIfMissing(UUID.randomUUID(), userId);
        Wallet wallet = wallets.lockByUserId(userId).orElseThrow();
        BigDecimal newBalance = wallet.getBalanceUzs().add(signedAmount);
        if (newBalance.signum() < 0) {
            throw new BusinessException("INSUFFICIENT_FUNDS", "error.wallet.insufficientFunds", wallet.getBalanceUzs());
        }
        wallet.setBalanceUzs(newBalance);
        WalletTransaction entry = ledger.save(new WalletTransaction(wallet.getId(), userId, type, signedAmount, newBalance,
                orderId, createdBy, comment, Instant.now(clock)));
        return toResponse(entry);
    }

    private void requireUser(UUID userId) {
        if (users.findById(userId).isEmpty()) {
            throw new NotFoundException("USER_NOT_FOUND", "error.user.notFound");
        }
    }

    private static void requirePositive(BigDecimal amount) {
        if (amount == null || amount.signum() <= 0) {
            throw new BusinessException("WALLET_AMOUNT_ZERO", "error.wallet.amountZero");
        }
    }

    private static WalletTransactionResponse toResponse(WalletTransaction entry) {
        return new WalletTransactionResponse(entry.getId(), entry.getType(), entry.getAmountUzs(), entry.getBalanceAfterUzs(),
                entry.getOrderId(), entry.getCreatedBy(), entry.getComment(), entry.getCreatedAt());
    }
}
