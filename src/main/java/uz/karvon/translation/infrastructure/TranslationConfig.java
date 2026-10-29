package uz.karvon.translation.infrastructure;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestClient;
import uz.karvon.translation.application.Translator;

import java.time.Duration;

/**
 * The real translator for every profile except dev and test (those use {@link FakeTranslator}),
 * chosen by {@code translation.provider}: google or yandex. The key comes from TRANSLATION_API_KEY.
 */
@Configuration
@Profile("!dev & !test")
public class TranslationConfig {

    private static final Duration TIMEOUT = Duration.ofSeconds(10);

    @Bean
    public Translator translator(@Value("${translation.provider}") String provider,
                                 @Value("${translation.api-key}") String apiKey,
                                 @Value("${translation.yandex.folder-id:}") String yandexFolderId,
                                 @Value("${translation.google.base-url:https://translation.googleapis.com}") String googleUrl,
                                 @Value("${translation.yandex.base-url:https://translate.api.cloud.yandex.net}") String yandexUrl) {
        if (apiKey.isBlank()) {
            throw new IllegalStateException("TRANSLATION_API_KEY is not set");
        }
        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(TIMEOUT);
        requestFactory.setReadTimeout(TIMEOUT);
        RestClient.Builder builder = RestClient.builder().requestFactory(requestFactory);
        return switch (provider.trim().toLowerCase()) {
            case "google" -> new GoogleTranslator(builder, googleUrl, apiKey);
            case "yandex" -> new YandexTranslator(builder, yandexUrl, apiKey, yandexFolderId);
            default -> throw new IllegalStateException("translation.provider must be google or yandex, not " + provider);
        };
    }
}
