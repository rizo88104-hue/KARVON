package uz.karvon.catalog.application;

import org.springframework.http.HttpStatus;
import uz.karvon.common.application.BusinessException;

/**
 * The product has a blocked keyword: it cannot be shown, added to the cart or ordered. HTTP 422.
 */
public class ProductBlockedException extends BusinessException {

    public ProductBlockedException() {
        super(HttpStatus.UNPROCESSABLE_CONTENT, "PRODUCT_BLOCKED", "error.catalog.productBlocked");
    }
}
