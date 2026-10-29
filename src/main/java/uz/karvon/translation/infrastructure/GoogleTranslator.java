package uz.karvon.translation.infrastructure;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import org.springframework.http.MediaType;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import uz.karvon.translation.application.TranslationUnavailableException;
import uz.karvon.translation.application.Translator;
import uz.karvon.translation.domain.Lang;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Google Cloud Translation API v2 (POST /language/translate/v2?key=...). Sends at most
 * {@value #CHUNK} texts per request, as plain text so no HTML entities come back.
 */
public class GoogleTranslator implements Translator {

    static final int CHUNK = 100;

    private final RestClient http;
    private final String apiKey;

    public GoogleTranslator(RestClient.Builder builder, String baseUrl, String apiKey) {
        this.http = builder.baseUrl(baseUrl).build();
        this.apiKey = apiKey;
    }

    @Override
    public String translate(String text, Lang from, Lang to) {
        return translateBatch(List.of(text), from, to).getFirst();
    }

    @Override
    public List<String> translateBatch(List<String> texts, Lang from, Lang to) {
        List<String> result = new ArrayList<>(texts.size());
        for (int start = 0; start < texts.size(); start += CHUNK) {
            result.addAll(request(texts.subList(start, Math.min(start + CHUNK, texts.size())), from, to));
        }
        return result;
    }

    private List<String> request(List<String> texts, Lang from, Lang to) {
        try {
            Response response = http.post()
                    .uri(uri -> uri.path("/language/translate/v2").queryParam("key", apiKey).build())
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(Map.of("q", texts, "source", from.code(), "target", to.code(), "format", "text"))
                    .retrieve()
                    .body(Response.class);
            if (response == null || response.data() == null || response.data().translations() == null
                    || response.data().translations().size() != texts.size()) {
                throw new IllegalStateException("Google returned a wrong number of translations");
            }
            return response.data().translations().stream().map(Translation::translatedText).toList();
        } catch (RestClientException | IllegalStateException ex) {
            throw new TranslationUnavailableException(ex);
        }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record Response(Data data) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record Data(List<Translation> translations) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record Translation(String translatedText) {
    }
}
