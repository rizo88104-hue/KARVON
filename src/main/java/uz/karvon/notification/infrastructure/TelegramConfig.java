package uz.karvon.notification.infrastructure;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestClient;
import uz.karvon.notification.application.TelegramSender;

/**
 * The real Telegram client for every profile except dev and test (those use {@link FakeTelegramSender}).
 */
@Configuration
@Profile("!dev & !test")
@EnableConfigurationProperties(TelegramBotProperties.class)
public class TelegramConfig {

    @Bean
    public TelegramSender telegramBotClient(TelegramBotProperties properties) {
        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(properties.timeout());
        requestFactory.setReadTimeout(properties.timeout());
        return new TelegramBotClient(RestClient.builder().requestFactory(requestFactory), properties);
    }
}
