package uz.karvon.payment.application.payme;

import java.util.Map;

/**
 * A Merchant API error answer. Codes from developer.help.paycom.uz ("Ошибки (ERRORS)", "Общие ошибки"); the
 * message is shown to the payer and must be localized (ru, uz, en).
 * For account errors the range -31050..-31099 is ours to use, with "data" = the account subfield ("order").
 */
public class PaymeError extends RuntimeException {

    // General errors (RPC).
    static final int NOT_POST = -32300;
    static final int PARSE_ERROR = -32700;
    static final int INVALID_REQUEST = -32600;
    static final int METHOD_NOT_FOUND = -32601;
    static final int INSUFFICIENT_PRIVILEGES = -32504;
    static final int SYSTEM_ERROR = -32400;
    // Merchant server errors.
    static final int INVALID_AMOUNT = -31001;
    static final int TRANSACTION_NOT_FOUND = -31003;
    static final int CANNOT_CANCEL = -31007;
    static final int CANNOT_PERFORM = -31008;
    // Our codes inside the documented account range -31050..-31099.
    static final int ORDER_NOT_FOUND = -31050;
    static final int ORDER_NOT_PAYABLE = -31051;
    static final int OTHER_PAYMENT_IN_PROGRESS = -31052;

    private static final Map<Integer, String[]> MESSAGES = Map.ofEntries(
            Map.entry(NOT_POST, texts("Метод запроса должен быть POST", "So'rov usuli POST bo'lishi kerak",
                    "Request method must be POST")),
            Map.entry(PARSE_ERROR, texts("Ошибка разбора JSON", "JSON xato", "JSON parse error")),
            Map.entry(INVALID_REQUEST, texts("Неверный RPC-запрос", "Noto'g'ri RPC so'rov", "Invalid RPC request")),
            Map.entry(METHOD_NOT_FOUND, texts("Метод не найден", "Metod topilmadi", "Method not found")),
            Map.entry(INSUFFICIENT_PRIVILEGES, texts("Недостаточно привилегий", "Ruxsat yo'q", "Insufficient privileges")),
            Map.entry(SYSTEM_ERROR, texts("Системная ошибка", "Tizim xatosi", "System error")),
            Map.entry(INVALID_AMOUNT, texts("Неверная сумма", "Noto'g'ri summa", "Invalid amount")),
            Map.entry(TRANSACTION_NOT_FOUND, texts("Транзакция не найдена", "Tranzaksiya topilmadi", "Transaction not found")),
            Map.entry(CANNOT_CANCEL, texts("Заказ уже выполняется, отмена невозможна",
                    "Buyurtma bajarilmoqda, bekor qilib bo'lmaydi", "The order is being fulfilled, cannot cancel")),
            Map.entry(CANNOT_PERFORM, texts("Невозможно выполнить операцию", "Amalni bajarib bo'lmaydi",
                    "Operation cannot be performed")),
            Map.entry(ORDER_NOT_FOUND, texts("Заказ не найден", "Buyurtma topilmadi", "Order not found")),
            Map.entry(ORDER_NOT_PAYABLE, texts("Заказ сейчас нельзя оплатить", "Buyurtmani hozir to'lab bo'lmaydi",
                    "The order cannot be paid now")),
            Map.entry(OTHER_PAYMENT_IN_PROGRESS, texts("По заказу уже идёт другая оплата",
                    "Buyurtma bo'yicha boshqa to'lov jarayonda", "Another payment for this order is in progress")));

    private final int code;
    private final String data;

    public PaymeError(int code) {
        this(code, null);
    }

    public PaymeError(int code, String data) {
        super("Payme error " + code);
        this.code = code;
        this.data = data;
    }

    public int code() {
        return code;
    }

    public String data() {
        return data;
    }

    public Map<String, String> message() {
        String[] texts = MESSAGES.getOrDefault(code, MESSAGES.get(SYSTEM_ERROR));
        return Map.of("ru", texts[0], "uz", texts[1], "en", texts[2]);
    }

    private static String[] texts(String ru, String uz, String en) {
        return new String[]{ru, uz, en};
    }
}
