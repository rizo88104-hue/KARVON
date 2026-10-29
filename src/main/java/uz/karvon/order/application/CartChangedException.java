package uz.karvon.order.application;

import org.springframework.http.HttpStatus;
import uz.karvon.common.application.BusinessException;
import uz.karvon.order.api.dto.CartChange;

import java.util.List;

/**
 * Prices or stock of selected cart lines changed when they were refreshed before ordering. HTTP 409 with the
 * list of changes; the cart already shows the new state, so the customer only has to confirm again.
 */
public class CartChangedException extends BusinessException {

    public CartChangedException(List<CartChange> changes) {
        super(HttpStatus.CONFLICT, "CART_CHANGED", "error.order.cartChanged");
        withProperty("changes", changes);
    }
}
