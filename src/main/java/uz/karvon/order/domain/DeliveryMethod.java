package uz.karvon.order.domain;

public enum DeliveryMethod {
    /** O'zbekiston pochtasi to the customer's address. */
    POST,
    /** The customer collects the parcel at the cargo company's office; no address needed. */
    CARGO_PICKUP,
    /** Courier to the customer's address. */
    COURIER;

    public boolean needsAddress() {
        return this != CARGO_PICKUP;
    }
}
