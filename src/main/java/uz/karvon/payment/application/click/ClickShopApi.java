package uz.karvon.payment.application.click;

import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import uz.karvon.order.application.OrderPaymentService;
import uz.karvon.order.application.PayableOrder;
import uz.karvon.payment.application.PaymentService;
import uz.karvon.payment.domain.PaymentAccount;
import uz.karvon.payment.domain.PaymentProvider;
import uz.karvon.payment.domain.PaymentState;
import uz.karvon.payment.domain.PaymentTransaction;
import uz.karvon.payment.infrastructure.PaymentProperties;
import uz.karvon.payment.infrastructure.PaymentTransactionRepository;
import uz.karvon.wallet.application.WalletService;
import uz.karvon.wallet.domain.WalletTransactionType;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Click SHOP API (docs.click.uz/shop-api/requests, /shop-api/errors). Click posts form-urlencoded parameters;
 * we answer JSON. Prepare (action 0) checks the order and creates our transaction; Complete (action 1) performs it.
 * <pre>
 * Prepare  sign = md5(click_trans_id + service_id + SECRET_KEY + merchant_trans_id + amount + action + sign_time)
 * Complete sign = md5(click_trans_id + service_id + SECRET_KEY + merchant_trans_id + merchant_prepare_id + amount
 *                     + action + sign_time)
 * </pre>
 * merchant_trans_id is our {@link PaymentAccount}; merchant_prepare_id and merchant_confirm_id are our transaction
 * number. Repeated calls are answered from the stored transaction.
 */
@Service
@RequiredArgsConstructor
public class ClickShopApi {

    // Error codes returned by the merchant system (Shop API, "Ошибки").
    static final int SUCCESS = 0;
    static final int SIGN_CHECK_FAILED = -1;
    static final int INCORRECT_AMOUNT = -2;
    static final int ACTION_NOT_FOUND = -3;
    static final int ALREADY_PAID = -4;
    static final int USER_DOES_NOT_EXIST = -5;
    static final int TRANSACTION_DOES_NOT_EXIST = -6;
    public static final int FAILED_TO_UPDATE_USER = -7;
    static final int ERROR_IN_REQUEST = -8;
    static final int TRANSACTION_CANCELLED = -9;

    private static final Map<Integer, String> NOTES = Map.of(
            SUCCESS, "Success",
            SIGN_CHECK_FAILED, "SIGN CHECK FAILED!",
            INCORRECT_AMOUNT, "Incorrect parameter amount",
            ACTION_NOT_FOUND, "Action not found",
            ALREADY_PAID, "Already paid",
            USER_DOES_NOT_EXIST, "User does not exist",
            TRANSACTION_DOES_NOT_EXIST, "Transaction does not exist",
            FAILED_TO_UPDATE_USER, "Failed to update user",
            ERROR_IN_REQUEST, "Error in request from click",
            TRANSACTION_CANCELLED, "Transaction cancelled");

    private static final List<String> PREPARE_FIELDS = List.of("click_trans_id", "service_id", "click_paydoc_id",
            "merchant_trans_id", "amount", "action", "error", "error_note", "sign_time", "sign_string");

    private static final Logger log = LoggerFactory.getLogger(ClickShopApi.class);

    private final PaymentTransactionRepository transactions;
    private final PaymentService payments;
    private final OrderPaymentService orders;
    private final WalletService wallets;
    private final PaymentProperties properties;
    private final Clock clock;

    @Transactional
    public Map<String, Object> prepare(Map<String, String> params) {
        Integer invalid = validate(params, PREPARE_FIELDS, "0", null);
        if (invalid != null) {
            return error(invalid);
        }
        String clickTransId = params.get("click_trans_id");
        PaymentTransaction existing = transactions.lockByProviderId(PaymentProvider.CLICK, clickTransId).orElse(null);
        if (existing != null) {
            return switch (existing.getState()) {
                case CREATED -> answer(params, "merchant_prepare_id", existing);
                case PERFORMED -> error(ALREADY_PAID);
                default -> error(TRANSACTION_CANCELLED);
            };
        }
        PaymentAccount account = PaymentAccount.parse(params.get("merchant_trans_id")).orElse(null);
        PayableOrder order = account == null ? null : orders.find(account.orderNumber(), account.purpose()).orElse(null);
        if (order == null) {
            return error(USER_DOES_NOT_EXIST);
        }
        switch (order.state()) {
            case PAYABLE -> {
            }
            case ALREADY_PAID -> {
                return error(ALREADY_PAID);
            }
            // TODO: ask which Click code fits a cancelled order / a shipping difference not yet due; the Shop API
            //  documents only -5 "User / order not found" for an order that cannot be found.
            default -> {
                return error(USER_DOES_NOT_EXIST);
            }
        }
        BigDecimal amount = new BigDecimal(params.get("amount"));
        if (order.amountDueUzs().compareTo(amount) != 0) {
            return error(INCORRECT_AMOUNT);
        }
        PaymentTransaction tx = payments.create(order, PaymentProvider.CLICK, clickTransId, null);
        return answer(params, "merchant_prepare_id", tx);
    }

    /**
     * Complete. A negative "error" from Click means the payment failed: we cancel and answer -9 ("Upon receiving a
     * negative error code, the Merchant must cancel the payment ... and return error code -9").
     * After a successful Prepare and debit the answer must not be an error, except -4 and -9; if the order can no
     * longer take the payment (e.g. it was cancelled meanwhile) we accept the money into the customer wallet.
     */
    @Transactional
    public Map<String, Object> complete(Map<String, String> params) {
        List<String> fields = new ArrayList<>(PREPARE_FIELDS);
        fields.add("merchant_prepare_id");
        Integer invalid = validate(params, fields, "1", params.get("merchant_prepare_id"));
        if (invalid != null) {
            return error(invalid);
        }
        long prepareId;
        try {
            prepareId = Long.parseLong(params.get("merchant_prepare_id"));
        } catch (NumberFormatException ex) {
            return error(TRANSACTION_DOES_NOT_EXIST);
        }
        PaymentTransaction tx = transactions.lockByNumber(PaymentProvider.CLICK, prepareId)
                .filter(found -> found.getProviderTransactionId().equals(params.get("click_trans_id")))
                .orElse(null);
        if (tx == null) {
            return error(TRANSACTION_DOES_NOT_EXIST);
        }
        if (tx.getState() == PaymentState.PERFORMED) {
            return error(ALREADY_PAID);
        }
        if (tx.getState() != PaymentState.CREATED) {
            return error(TRANSACTION_CANCELLED);
        }
        if (Integer.parseInt(params.get("error")) < 0) {
            tx.cancel(Instant.now(clock), null);
            return error(TRANSACTION_CANCELLED);
        }
        if (tx.getAmountUzs().compareTo(new BigDecimal(params.get("amount"))) != 0) {
            return error(INCORRECT_AMOUNT);
        }
        if (payments.canComplete(tx)) {
            payments.complete(tx);
        } else {
            // TODO: reverse through CLICK-API-MERCHANT payment/reversal, as the Shop API asks; until then the
            //  money is kept for the customer in the wallet.
            log.warn("Click paid order {} that can no longer take it; crediting the wallet", tx.getOrderNumber());
            tx.perform(Instant.now(clock));
            wallets.credit(tx.getCustomerId(), tx.getAmountUzs(), WalletTransactionType.REFUND, tx.getOrderId(), null,
                    "Click payment for " + tx.account().value() + " after the order could not take it");
        }
        return answer(params, "merchant_confirm_id", tx);
    }

    /** Required fields, numbers, signature, action and our service id. Null when valid. */
    private Integer validate(Map<String, String> params, List<String> required, String action, String prepareId) {
        for (String field : required) {
            if (!params.containsKey(field) || (!field.equals("error_note") && params.get(field).isBlank())) {
                return ERROR_IN_REQUEST;
            }
        }
        try {
            new BigDecimal(params.get("amount"));
            Integer.parseInt(params.get("error"));
        } catch (NumberFormatException ex) {
            return ERROR_IN_REQUEST;
        }
        String expected = md5(params.get("click_trans_id") + params.get("service_id") + properties.click().secretKey()
                + params.get("merchant_trans_id") + (prepareId == null ? "" : prepareId) + params.get("amount")
                + params.get("action") + params.get("sign_time"));
        if (properties.click().secretKey().isBlank() || !expected.equalsIgnoreCase(params.get("sign_string"))) {
            return SIGN_CHECK_FAILED;
        }
        if (!action.equals(params.get("action"))) {
            return ACTION_NOT_FOUND;
        }
        // TODO: the Shop API does not say which code to answer for a service_id that is not ours; -8 for now.
        if (!properties.click().serviceId().equals(params.get("service_id"))) {
            return ERROR_IN_REQUEST;
        }
        return null;
    }

    private static Map<String, Object> answer(Map<String, String> params, String idField, PaymentTransaction tx) {
        Map<String, Object> answer = new LinkedHashMap<>();
        answer.put("click_trans_id", Long.parseLong(params.get("click_trans_id")));
        answer.put("merchant_trans_id", params.get("merchant_trans_id"));
        answer.put(idField, tx.getNumber());
        answer.put("error", SUCCESS);
        answer.put("error_note", NOTES.get(SUCCESS));
        return answer;
    }

    private static Map<String, Object> error(int code) {
        Map<String, Object> answer = new LinkedHashMap<>();
        answer.put("error", code);
        answer.put("error_note", NOTES.get(code));
        return answer;
    }

    static String md5(String text) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("MD5").digest(text.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("MD5 is not available", ex);
        }
    }
}
