package uz.karvon.payment.domain;

/**
 * Payment states. The numbers are the Payme Merchant API transaction states
 * (developer.help.paycom.uz, "Типы данных": 1, 2, -1, -2).
 */
public enum PaymentState {
    CREATED(1), PERFORMED(2), CANCELLED(-1), CANCELLED_AFTER_PERFORM(-2);

    private final int paymeCode;

    PaymentState(int paymeCode) {
        this.paymeCode = paymeCode;
    }

    public int paymeCode() {
        return paymeCode;
    }
}
