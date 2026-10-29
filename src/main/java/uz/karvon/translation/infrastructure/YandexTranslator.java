package uz.karvon.translation.infrastructure;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import uz.karvon.translation.application.TranslationUnavailableException;
import uz.karvon.translation.application.Translator;
import uz.karvon.translation.domain.Lang;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Yandex Cloud Translate API v2 (POST /translate/v2/translate, "Authorization: Api-Key ..."). The folder id is
 * needed only for user accounts; a service account API key works without it.
 */
public class YandexTranslator implements Translator {

    static final int CHUNK = 100;

    private final RestClient http;
    private final String apiKey;
    private final String folderId;

    public YandexTranslator(RestClient.Builder builder, String baseUrl, String apiKey, String folderId) {
        this.http = builder.baseUrl(baseUrl).build();
        this.apiKey = apiKey;
        this.folderId = folderId;
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
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("texts", texts);
        body.put("sourceLanguageCode", from.code());
        body.put("targetLanguageCode", to.code());
        if (folderId != null && !folderId.isBlank()) {
            body.put("folderId", folderId);
        }
        try {
            Response response = http.post()
                    .uri("/translate/v2/translate")
                    .header(HttpHeaders.AUTHORIZATION, "Api-Key " + apiKey)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(body)
                    .retrieve()
                    .body(Response.class);
            if (response == null || response.translations() == null || response.translations().size() != texts.size()) {
                throw new IllegalStateException("Yandex returned a wrong number of translations");
            }
            return response.translations().stream().map(Translation::text).toList();
        } catch (RestClientException | IllegalStateException ex) {
            throw new TranslationUnavailableException(ex);
        }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record Response(List<Translation> translations) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record Translation(String text) {
    }
}
