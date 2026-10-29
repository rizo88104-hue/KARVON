package uz.karvon.settings.infrastructure;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestClient;
import tools.jackson.databind.json.JsonMapper;
import uz.karvon.settings.application.CbuRateClient;

import java.time.Duration;

/**
 * The real Central Bank client for every profile except dev and test (those use {@link FakeCbuRateClient}).
 */
@Configuration
@Profile("!dev & !test")
public class CbuConfig {

    private static final Duration TIMEOUT = Duration.ofSeconds(5);

    @Bean
    public CbuRateClient cbuRateClient(@Value("${karvon.cbu.base-url:https://cbu.uz}") String baseUrl, JsonMapper json) {
        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(TIMEOUT);
        requestFactory.setReadTimeout(TIMEOUT);
        return new HttpCbuRateClient(RestClient.builder().requestFactory(requestFactory), baseUrl, json);
    }
}
