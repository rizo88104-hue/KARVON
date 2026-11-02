package uz.karvon.notification.infrastructure;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestClient;
import uz.karvon.notification.application.TelegramBotClient;

/**
 * The real Telegram client for every profile except dev and test (those use {@link FakeTelegramBotClient}).
 */
@Configuration
@Profile("!dev & !test")
public class TelegramConfig {

    @Bean
    public TelegramBotClient httpTelegramBotClient(TelegramProperties properties) {
        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(properties.timeout());
        requestFactory.setReadTimeout(properties.timeout());
        return new HttpTelegramBotClient(RestClient.builder().requestFactory(requestFactory), properties);
    }
}
