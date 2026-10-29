package uz.karvon.catalog.application;

import uz.karvon.common.application.NotFoundException;

public class ProductNotFoundException extends NotFoundException {

    public ProductNotFoundException() {
        super("PRODUCT_NOT_FOUND", "error.catalog.productNotFound");
    }
}
