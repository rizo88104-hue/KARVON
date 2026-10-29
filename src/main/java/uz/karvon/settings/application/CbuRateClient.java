package uz.karvon.settings.application;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * The official CNY rate of the Central Bank of Uzbekistan. Reference only: our rate is set by ADMIN.
 * Throws {@link CbuUnavailableException} when the bank cannot be reached.
 */
public interface CbuRateClient {

    CbuRate cnyRate();

    /** {@code rate} is UZS for 1 CNY. */
    record CbuRate(BigDecimal rate, LocalDate date) {
    }
}
