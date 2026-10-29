package uz.karvon.catalog.application;

import org.springframework.http.HttpStatus;
import uz.karvon.common.application.BusinessException;

/**
 * The Taobao API is down, slow or the circuit breaker is open. Answered with HTTP 503.
 */
public class CatalogUnavailableException extends BusinessException {

    public CatalogUnavailableException(Throwable cause) {
        super(HttpStatus.SERVICE_UNAVAILABLE, "CATALOG_UNAVAILABLE", "error.catalog.unavailable");
        initCause(cause);
    }
}
