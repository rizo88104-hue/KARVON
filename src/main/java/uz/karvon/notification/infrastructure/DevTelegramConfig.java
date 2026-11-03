package uz.karvon.notification.infrastructure;

import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestClient;
import uz.karvon.notification.application.TelegramService;

/**
 * The bot in the dev profile. With TELEGRAM_BOT_TOKEN (and TELEGRAM_BOT_USERNAME) in .env: the real bot, updates by
 * long polling ({@link TelegramPoller}). Without a token: {@link FakeTelegramBotClient}, which only logs; then
 * POST /api/v1/dev/otp/{phone}/telegram-login stands in for the bot.
 */
@Configuration
@Profile("dev")
public class DevTelegramConfig {

    static final String TOKEN_SET = "!'${karvon.telegram.bot-token:}'.trim().isEmpty()";
    static final String NO_TOKEN = "'${karvon.telegram.bot-token:}'.trim().isEmpty()";

    @Bean
    @ConditionalOnExpression(TOKEN_SET)
    public HttpTelegramBotClient devTelegramBotClient(TelegramProperties properties) {
        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(properties.timeout());
        requestFactory.setReadTimeout(properties.timeout());
        return new HttpTelegramBotClient(RestClient.builder().requestFactory(requestFactory), properties);
    }

    @Bean
    @ConditionalOnExpression(TOKEN_SET)
    public TelegramPoller telegramPoller(TelegramProperties properties, TelegramService bot) {
        return new TelegramPoller(properties, bot);
    }

    @Bean
    @ConditionalOnExpression(NO_TOKEN)
    public FakeTelegramBotClient devFakeTelegramBotClient() {
        return new FakeTelegramBotClient();
    }
}
