package uz.karvon.notification.infrastructure;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestClient;
import uz.karvon.notification.application.SmsSender;

/**
 * The real SMS sender for every profile except dev and test (those use {@link FakeSmsSender}).
 */
@Configuration
@Profile("!dev & !test")
@EnableConfigurationProperties(EskizProperties.class)
public class SmsConfig {

    @Bean
    public SmsSender eskizSmsSender(EskizProperties properties) {
        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(properties.timeout());
        requestFactory.setReadTimeout(properties.timeout());
        return new EskizSmsSender(RestClient.builder().requestFactory(requestFactory), properties);
    }
}
