package uz.karvon.translation.infrastructure;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;
import uz.karvon.translation.application.TranslationUnavailableException;
import uz.karvon.translation.domain.Lang;

import java.util.List;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.ExpectedCount.once;
import static org.springframework.test.web.client.ExpectedCount.times;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.jsonPath;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

class HttpTranslatorsTest {

    private final RestClient.Builder builder = RestClient.builder();
    private final MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();

    @Test
    void googleSendsAllTextsAsPlainText() {
        GoogleTranslator google = new GoogleTranslator(builder, "https://google.test", "g-key");
        server.expect(once(), requestTo("https://google.test/language/translate/v2?key=g-key"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(jsonPath("$.q[0]").value("运动鞋"))
                .andExpect(jsonPath("$.q[1]").value("黑色"))
                .andExpect(jsonPath("$.source").value("zh"))
                .andExpect(jsonPath("$.target").value("uz"))
                .andExpect(jsonPath("$.format").value("text"))
                .andRespond(withSuccess("""
                        {"data":{"translations":[{"translatedText":"Krossovka"},{"translatedText":"Qora"}]}}""",
                        MediaType.APPLICATION_JSON));

        assertThat(google.translateBatch(List.of("运动鞋", "黑色"), Lang.ZH, Lang.UZ)).containsExactly("Krossovka", "Qora");
        server.verify();
    }

    @Test
    void googleSplitsLargeBatches() {
        GoogleTranslator google = new GoogleTranslator(builder, "https://google.test", "g-key");
        String hundred = IntStream.range(0, GoogleTranslator.CHUNK).mapToObj(i -> "{\"translatedText\":\"t\"}")
                .reduce((a, b) -> a + "," + b).orElseThrow();
        server.expect(once(), requestTo("https://google.test/language/translate/v2?key=g-key"))
                .andRespond(withSuccess("{\"data\":{\"translations\":[" + hundred + "]}}", MediaType.APPLICATION_JSON));
        server.expect(once(), requestTo("https://google.test/language/translate/v2?key=g-key"))
                .andRespond(withSuccess("{\"data\":{\"translations\":[{\"translatedText\":\"last\"}]}}",
                        MediaType.APPLICATION_JSON));

        List<String> texts = IntStream.range(0, GoogleTranslator.CHUNK + 1).mapToObj(i -> "x" + i).toList();

        assertThat(google.translateBatch(texts, Lang.ZH, Lang.RU)).hasSize(GoogleTranslator.CHUNK + 1).endsWith("last");
        server.verify();
    }

    @Test
    void yandexUsesApiKeyHeaderAndFolder() {
        YandexTranslator yandex = new YandexTranslator(builder, "https://yandex.test", "y-key", "folder-1");
        server.expect(once(), requestTo("https://yandex.test/translate/v2/translate"))
                .andExpect(header(HttpHeaders.AUTHORIZATION, "Api-Key y-key"))
                .andExpect(jsonPath("$.texts[0]").value("кроссовки"))
                .andExpect(jsonPath("$.sourceLanguageCode").value("ru"))
                .andExpect(jsonPath("$.targetLanguageCode").value("zh"))
                .andExpect(jsonPath("$.folderId").value("folder-1"))
                .andRespond(withSuccess("{\"translations\":[{\"text\":\"运动鞋\"}]}", MediaType.APPLICATION_JSON));

        assertThat(yandex.translate("кроссовки", Lang.RU, Lang.ZH)).isEqualTo("运动鞋");
        server.verify();
    }

    @Test
    void providerErrorsBecomeTranslationUnavailable() {
        YandexTranslator yandex = new YandexTranslator(builder, "https://yandex.test", "y-key", "");
        GoogleTranslator google = new GoogleTranslator(builder, "https://google.test", "g-key");
        server.expect(times(1), requestTo("https://yandex.test/translate/v2/translate")).andRespond(withServerError());
        server.expect(times(1), requestTo("https://google.test/language/translate/v2?key=g-key"))
                .andRespond(withSuccess("{\"data\":{\"translations\":[]}}", MediaType.APPLICATION_JSON));

        assertThatThrownBy(() -> yandex.translate("a", Lang.UZ, Lang.ZH)).isInstanceOf(TranslationUnavailableException.class);
        assertThatThrownBy(() -> google.translate("a", Lang.UZ, Lang.ZH)).isInstanceOf(TranslationUnavailableException.class);
    }
}
