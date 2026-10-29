package uz.karvon.common.infrastructure;

import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * Turns on @Scheduled jobs (e.g. cancelling unpaid orders).
 */
@Configuration
@EnableScheduling
public class SchedulingConfig {
}
