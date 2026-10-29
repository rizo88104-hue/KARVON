package uz.karvon.payment.infrastructure;

import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import uz.karvon.payment.domain.PaymentProvider;
import uz.karvon.payment.domain.PaymentProviderLog;

import java.time.Clock;
import java.time.Instant;
import java.util.UUID;

/**
 * Saves raw provider calls in their own transaction, so error answers are kept even when the payment change
 * was rolled back. A failure to log never breaks the payment.
 */
@Component
@RequiredArgsConstructor
public class PaymentLogWriter {

    private static final Logger log = LoggerFactory.getLogger(PaymentLogWriter.class);

    private final PaymentProviderLogRepository logs;
    private final Clock clock;

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void write(PaymentProvider provider, String operation, String request, String response, UUID transactionId) {
        try {
            logs.save(new PaymentProviderLog(provider, operation, request, response, transactionId, Instant.now(clock)));
        } catch (RuntimeException ex) {
            log.error("Could not save {} {} log", provider, operation, ex);
        }
    }
}
