package uz.karvon.catalog.infrastructure;

import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerConfig;
import io.github.resilience4j.retry.Retry;
import io.github.resilience4j.retry.RetryConfig;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import tools.jackson.databind.json.JsonMapper;
import uz.karvon.catalog.application.ProductNotFoundException;
import uz.karvon.catalog.application.TaobaoProvider;

import java.net.http.HttpClient;
import java.time.Duration;

/**
 * The real Taobao provider for every profile except dev and test (those use {@link FakeTaobaoProvider}).
 */
@Configuration
@Profile("!dev & !test")
public class TaobaoConfig {

    static final Duration TIMEOUT = Duration.ofSeconds(5);
    static final int RETRIES = 2;

    @Bean
    public TaobaoProvider taobaoProvider(@Value("${karvon.taobao.api-url}") String apiUrl,
                                         @Value("${karvon.taobao.api-key}") String apiKey,
                                         JsonMapper json) {
        if (apiUrl.isBlank() || apiKey.isBlank()) {
            throw new IllegalStateException("TAOBAO_API_URL and TAOBAO_API_KEY must be set");
        }
        RestClient http = RestClient.builder().baseUrl(apiUrl).requestFactory(requestFactory()).build();
        return new HttpTaobaoProvider(http, apiKey, new TaobaoResponseMapper(json),
                circuitBreaker(), retry(Duration.ofMillis(300)),
                new ShortLinkResolver(RestClient.builder().requestFactory(requestFactory())));
    }

    /** Opens when half of the last 20 calls (at least 10) failed; tries again after 30 s. */
    public static CircuitBreaker circuitBreaker() {
        return CircuitBreaker.of("taobao", CircuitBreakerConfig.custom()
                .slidingWindowSize(20)
                .minimumNumberOfCalls(10)
                .failureRateThreshold(50)
                .waitDurationInOpenState(Duration.ofSeconds(30))
                .ignoreExceptions(ProductNotFoundException.class)
                .build());
    }

    /** 1 call + {@value #RETRIES} retries, only for errors that may pass: network, 5xx, 429. */
    public static Retry retry(Duration wait) {
        return Retry.of("taobao", RetryConfig.custom()
                .maxAttempts(RETRIES + 1)
                .waitDuration(wait)
                .retryExceptions(ResourceAccessException.class, HttpServerErrorException.class,
                        HttpClientErrorException.TooManyRequests.class)
                .build());
    }

    /** 5 s to connect and to read; redirects are not followed (needed by the short link resolver). */
    private static JdkClientHttpRequestFactory requestFactory() {
        HttpClient client = HttpClient.newBuilder()
                .connectTimeout(TIMEOUT)
                .followRedirects(HttpClient.Redirect.NEVER)
                .build();
        JdkClientHttpRequestFactory factory = new JdkClientHttpRequestFactory(client);
        factory.setReadTimeout(TIMEOUT);
        return factory;
    }
}
