package uz.karvon.payment.application.payme;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import uz.karvon.order.application.OrderPaymentService;
import uz.karvon.order.application.PayableOrder;
import uz.karvon.payment.application.PaymentService;
import uz.karvon.payment.domain.PaymentAccount;
import uz.karvon.payment.domain.PaymentProvider;
import uz.karvon.payment.domain.PaymentState;
import uz.karvon.payment.domain.PaymentTransaction;
import uz.karvon.payment.infrastructure.PaymeAmounts;
import uz.karvon.payment.infrastructure.PaymentTransactionRepository;

import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * The six Payme Merchant API methods (developer.help.paycom.uz/metody-merchant-api). Each runs in one transaction.
 * Errors are thrown as {@link PaymeError}; a timeout cancellation is saved even though an error is answered.
 * The account object has one field, "order" (see {@link PaymentAccount}).
 */
@Service
@RequiredArgsConstructor
public class PaymeMethods {

    static final String ACCOUNT_FIELD = "order";
    /** "Отмена транзакции по таймауту производится через 12 часов — 43 200 000 миллисекунд". */
    static final long TIMEOUT_MS = 43_200_000L;
    /** Cancel reason 4: "Транзакция отменена по таймауту". */
    static final int REASON_TIMEOUT = 4;

    private final PaymentTransactionRepository transactions;
    private final PaymentService payments;
    private final OrderPaymentService orders;
    private final Clock clock;

    /** CheckPerformTransaction: {allow: true}, or -31050..-31099 for the account, -31001 for the amount. */
    @Transactional(readOnly = true)
    public Map<String, Object> checkPerformTransaction(long amount, Map<String, Object> account) {
        payableFor(account, amount);
        return Map.of("allow", true);
    }

    /**
     * CreateTransaction. Repeated with the same id: the same answer while the transaction is still waiting
     * ("Если транзакция уже создана ... производит базовую проверку"), -31008 otherwise; after 12 hours it is cancelled
     * with reason 4. A second, different transaction for an order that already has one waiting gets -31052.
     */
    @Transactional(noRollbackFor = PaymeError.class)
    public Map<String, Object> createTransaction(String id, long time, long amount, Map<String, Object> account) {
        PaymentTransaction existing = transactions.lockByProviderId(PaymentProvider.PAYME, id).orElse(null);
        if (existing != null) {
            if (existing.getState() != PaymentState.CREATED) {
                throw new PaymeError(PaymeError.CANNOT_PERFORM);
            }
            if (expired(existing)) {
                existing.cancel(now(), REASON_TIMEOUT);
                throw new PaymeError(PaymeError.CANNOT_PERFORM);
            }
            return createAnswer(existing);
        }
        PayableOrder order = payableFor(account, amount);
        if (transactions.existsByOrderIdAndPurposeAndProviderAndState(order.orderId(), order.purpose(),
                PaymentProvider.PAYME, PaymentState.CREATED)) {
            throw new PaymeError(PaymeError.OTHER_PAYMENT_IN_PROGRESS, ACCOUNT_FIELD);
        }
        return createAnswer(payments.create(order, PaymentProvider.PAYME, id, time));
    }

    /** PerformTransaction: pays the order once; repeated calls answer the same; -31008 if it cannot be paid. */
    @Transactional(noRollbackFor = PaymeError.class)
    public Map<String, Object> performTransaction(String id) {
        PaymentTransaction tx = load(id);
        if (tx.getState() == PaymentState.CREATED) {
            if (expired(tx)) {
                tx.cancel(now(), REASON_TIMEOUT);
                throw new PaymeError(PaymeError.CANNOT_PERFORM);
            }
            if (!payments.canComplete(tx)) {
                throw new PaymeError(PaymeError.CANNOT_PERFORM);
            }
            payments.complete(tx);
        } else if (tx.getState() != PaymentState.PERFORMED) {
            throw new PaymeError(PaymeError.CANNOT_PERFORM);
        }
        Map<String, Object> answer = new LinkedHashMap<>();
        answer.put("transaction", String.valueOf(tx.getNumber()));
        answer.put("perform_time", millis(tx.getPerformTime()));
        answer.put("state", tx.getState().paymeCode());
        return answer;
    }

    /**
     * CancelTransaction: a waiting transaction becomes -1; a performed one becomes -2 and the order payment is
     * reversed, unless the order is already being fulfilled (-31007). Repeated calls answer the same.
     */
    @Transactional(noRollbackFor = PaymeError.class)
    public Map<String, Object> cancelTransaction(String id, int reason) {
        PaymentTransaction tx = load(id);
        if (tx.getState() == PaymentState.CREATED) {
            tx.cancel(now(), reason);
        } else if (tx.getState() == PaymentState.PERFORMED) {
            if (!orders.canReverse(tx.getOrderId(), tx.getPurpose())) {
                throw new PaymeError(PaymeError.CANNOT_CANCEL);
            }
            orders.paymentReversed(tx.getOrderId(), tx.getPurpose(), tx.getAmountUzs(), "Payme cancelled the payment");
            tx.cancel(now(), reason);
        }
        Map<String, Object> answer = new LinkedHashMap<>();
        answer.put("transaction", String.valueOf(tx.getNumber()));
        answer.put("cancel_time", millis(tx.getCancelTime()));
        answer.put("state", tx.getState().paymeCode());
        return answer;
    }

    /** CheckTransaction: the stored state; times are 0 when not reached, reason null when not cancelled. */
    @Transactional(readOnly = true)
    public Map<String, Object> checkTransaction(String id) {
        PaymentTransaction tx = transactions.findByProviderAndProviderTransactionId(PaymentProvider.PAYME, id)
                .orElseThrow(() -> new PaymeError(PaymeError.TRANSACTION_NOT_FOUND));
        Map<String, Object> answer = new LinkedHashMap<>();
        answer.put("create_time", millis(tx.getCreateTime()));
        answer.put("perform_time", millis(tx.getPerformTime()));
        answer.put("cancel_time", millis(tx.getCancelTime()));
        answer.put("transaction", String.valueOf(tx.getNumber()));
        answer.put("state", tx.getState().paymeCode());
        answer.put("reason", tx.getReason());
        return answer;
    }

    /** GetStatement: transactions created in Payme Business with from <= time <= to, oldest first. */
    @Transactional(readOnly = true)
    public Map<String, Object> getStatement(long from, long to) {
        List<Map<String, Object>> list = new ArrayList<>();
        for (PaymentTransaction tx : transactions.findByProviderAndProviderTimeBetweenOrderByProviderTimeAsc(
                PaymentProvider.PAYME, from, to)) {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("id", tx.getProviderTransactionId());
            row.put("time", tx.getProviderTime());
            row.put("amount", PaymeAmounts.toTiyin(tx.getAmountUzs()));
            row.put("account", Map.of(ACCOUNT_FIELD, tx.account().value()));
            row.put("create_time", millis(tx.getCreateTime()));
            row.put("perform_time", millis(tx.getPerformTime()));
            row.put("cancel_time", millis(tx.getCancelTime()));
            row.put("transaction", String.valueOf(tx.getNumber()));
            row.put("state", tx.getState().paymeCode());
            row.put("reason", tx.getReason());
            list.add(row);
        }
        return Map.of("transactions", list);
    }

    /** Account and amount checks shared by CheckPerformTransaction and CreateTransaction. */
    private PayableOrder payableFor(Map<String, Object> account, long amount) {
        Object value = account == null ? null : account.get(ACCOUNT_FIELD);
        PaymentAccount parsed = PaymentAccount.parse(value == null ? null : value.toString())
                .orElseThrow(() -> new PaymeError(PaymeError.ORDER_NOT_FOUND, ACCOUNT_FIELD));
        PayableOrder order = orders.find(parsed.orderNumber(), parsed.purpose())
                .orElseThrow(() -> new PaymeError(PaymeError.ORDER_NOT_FOUND, ACCOUNT_FIELD));
        if (!order.payable()) {
            throw new PaymeError(PaymeError.ORDER_NOT_PAYABLE, ACCOUNT_FIELD);
        }
        if (PaymeAmounts.toTiyin(order.amountDueUzs()) != amount) {
            throw new PaymeError(PaymeError.INVALID_AMOUNT);
        }
        return order;
    }

    private Map<String, Object> createAnswer(PaymentTransaction tx) {
        Map<String, Object> answer = new LinkedHashMap<>();
        answer.put("create_time", millis(tx.getCreateTime()));
        answer.put("transaction", String.valueOf(tx.getNumber()));
        answer.put("state", tx.getState().paymeCode());
        return answer;
    }

    private PaymentTransaction load(String id) {
        return transactions.lockByProviderId(PaymentProvider.PAYME, id)
                .orElseThrow(() -> new PaymeError(PaymeError.TRANSACTION_NOT_FOUND));
    }

    /** 12 hours after the transaction was created in Payme Business. */
    private boolean expired(PaymentTransaction tx) {
        long created = tx.getProviderTime() != null ? tx.getProviderTime() : millis(tx.getCreateTime());
        return clock.millis() - created > TIMEOUT_MS;
    }

    private Instant now() {
        return Instant.now(clock);
    }

    private static long millis(Instant time) {
        return time == null ? 0 : time.toEpochMilli();
    }
}
