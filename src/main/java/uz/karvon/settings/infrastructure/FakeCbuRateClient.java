package uz.karvon.settings.infrastructure;

import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;
import uz.karvon.settings.application.CbuRateClient;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;

/**
 * Dev and test: a fixed rate dated today, no network.
 */
@Component
@Profile({"dev", "test"})
public class FakeCbuRateClient implements CbuRateClient {

    static final BigDecimal RATE = new BigDecimal("1750.00");

    private final Clock clock;

    public FakeCbuRateClient(Clock clock) {
        this.clock = clock;
    }

    @Override
    public CbuRate cnyRate() {
        return new CbuRate(RATE, LocalDate.now(clock));
    }
}
