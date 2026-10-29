package uz.karvon.common.infrastructure;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;

import java.time.Clock;

@Configuration
@EnableJpaAuditing
public class CommonConfig {

    @Bean
    public Clock clock() {
        return Clock.systemUTC();
    }
}
