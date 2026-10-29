package uz.karvon.payment.application;

import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import uz.karvon.common.application.BusinessException;
import uz.karvon.common.application.NotFoundException;
import uz.karvon.order.application.OrderPaymentService;
import uz.karvon.order.application.PayableOrder;
import uz.karvon.order.application.PaymentPurpose;
import uz.karvon.order.application.PendingPaymentCheck;
import uz.karvon.payment.api.dto.StartPaymentResponse;
import uz.karvon.payment.domain.PaymentAccount;
import uz.karvon.payment.domain.PaymentProvider;
import uz.karvon.payment.domain.PaymentState;
import uz.karvon.payment.domain.PaymentTransaction;
import uz.karvon.payment.infrastructure.PaymentTransactionRepository;
import uz.karvon.wallet.application.WalletService;
import uz.karvon.wallet.domain.WalletTransactionType;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

/**
 * Starting payments and the steps all providers share. "Completing" a payment marks the transaction PERFORMED,
 * updates the order (ORDER: PAID, SHIPPING_DIFFERENCE: difference paid) and publishes PaymentCompletedEvent,
 * all in the caller's transaction.
 */
@Service
@RequiredArgsConstructor
public class PaymentService implements PendingPaymentCheck {

    /** Payme cancels an unfinished transaction after 12 hours (CreateTransaction, "Отмена по таймауту"). */
    static final Duration PAYME_PENDING = Duration.ofHours(12);
    /** Click calls Complete right after Prepare; this is only a safety margin. */
    static final Duration CLICK_PENDING = Duration.ofHours(1);

    private final PaymentTransactionRepository transactions;
    private final OrderPaymentService orders;
    private final WalletService wallets;
    private final PaymentGateway gateway;
    private final ApplicationEventPublisher events;
    private final Clock clock;

    /**
     * WALLET pays at once (INSUFFICIENT_FUNDS if the balance is too small); PAYME and CLICK answer the checkout link.
     */
    @Transactional
    public StartPaymentResponse start(UUID customerId, String orderNumber, PaymentPurpose purpose, PaymentProvider provider) {
        PayableOrder order = orders.forCustomer(customerId, orderNumber, purpose);
        requirePayable(order);
        PaymentAccount account = new PaymentAccount(order.number(), purpose);
        if (provider == PaymentProvider.WALLET) {
            PaymentTransaction tx = create(order, PaymentProvider.WALLET, null, null);
            wallets.debit(customerId, order.amountDueUzs(),
                    purpose == PaymentPurpose.ORDER ? WalletTransactionType.ORDER_PAYMENT : WalletTransactionType.SHIPPING_PAYMENT,
                    order.orderId(), customerId, account.value());
            complete(tx);
            return new StartPaymentResponse(provider, order.amountDueUzs(), null, true, tx.getId());
        }
        UUID pendingId = null;
        if (gateway.simulated()) {
            pendingId = create(order, provider, "fake-" + UUID.randomUUID(), null).getId();
        }
        return new StartPaymentResponse(provider, order.amountDueUzs(),
                gateway.checkoutUrl(provider, account, order.amountDueUzs(), pendingId), false, pendingId);
    }

    /** Dev and test: the fake checkout page was opened. Repeating it changes nothing. */
    @Transactional
    public PaymentTransaction performSimulated(UUID transactionId) {
        PaymentTransaction tx = transactions.lockById(transactionId)
                .filter(found -> found.getProviderTransactionId() != null && found.getProviderTransactionId().startsWith("fake-"))
                .orElseThrow(() -> new NotFoundException("PAYMENT_NOT_FOUND", "error.payment.notFound"));
        if (tx.getState() == PaymentState.CREATED) {
            requirePayable(orders.get(tx.getOrderId(), tx.getPurpose()));
            complete(tx);
        }
        return tx;
    }

    /** A new CREATED transaction for the full amount due. */
    public PaymentTransaction create(PayableOrder order, PaymentProvider provider, String providerTransactionId,
                                     Long providerTime) {
        return transactions.save(new PaymentTransaction(transactions.nextNumber(), order.purpose(), order.orderId(),
                order.number(), order.customerId(), provider, providerTransactionId, order.amountDueUzs(), providerTime,
                Instant.now(clock)));
    }

    /** The money arrived: transaction PERFORMED and the order updated, together. */
    public void complete(PaymentTransaction tx) {
        Instant now = Instant.now(clock);
        orders.paymentReceived(tx.getOrderId(), tx.getPurpose(), tx.getAmountUzs());
        tx.perform(now);
        events.publishEvent(new PaymentCompletedEvent(tx.getId(), tx.getOrderId(), tx.getOrderNumber(), tx.getCustomerId(),
                tx.getPurpose(), tx.getProvider(), tx.getAmountUzs(), now));
    }

    /** Whether the order can take exactly this payment right now. */
    public boolean canComplete(PaymentTransaction tx) {
        PayableOrder order = orders.get(tx.getOrderId(), tx.getPurpose());
        return order.payable() && order.amountDueUzs().compareTo(tx.getAmountUzs()) == 0;
    }

    /** Auto-cancel of unpaid orders waits while Payme or Click is still processing a payment. */
    @Override
    @Transactional(readOnly = true)
    public boolean hasPendingPayment(UUID orderId) {
        Instant now = Instant.now(clock);
        return transactions.existsPending(orderId, PaymentProvider.PAYME,
                PaymentState.CREATED, now.minus(PAYME_PENDING))
                || transactions.existsPending(orderId, PaymentProvider.CLICK,
                PaymentState.CREATED, now.minus(CLICK_PENDING));
    }

    private static void requirePayable(PayableOrder order) {
        switch (order.state()) {
            case PAYABLE -> {
            }
            case ALREADY_PAID -> throw new BusinessException("ORDER_ALREADY_PAID", "error.payment.alreadyPaid");
            case CANCELLED -> throw new BusinessException("ORDER_CANCELLED", "error.payment.orderCancelled");
            case NOT_DUE -> throw new BusinessException("NOTHING_TO_PAY", "error.payment.nothingToPay");
        }
        if (order.amountDueUzs().compareTo(BigDecimal.ZERO) <= 0) {
            throw new BusinessException("NOTHING_TO_PAY", "error.payment.nothingToPay");
        }
    }
}
