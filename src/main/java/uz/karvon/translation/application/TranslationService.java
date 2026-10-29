package uz.karvon.translation.application;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import uz.karvon.common.domain.Hashes;
import uz.karvon.translation.domain.Lang;
import uz.karvon.translation.infrastructure.TranslationCacheStore;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Translation for the other modules. Every string is sent to the provider only once:
 * results are kept in translation_cache under the SHA-256 of the trimmed source text.
 * Blank strings and same-language requests are returned as they are.
 */
@Service
@RequiredArgsConstructor
public class TranslationService {

    private final Translator translator;
    private final TranslationCacheStore cache;

    public String translate(String text, Lang from, Lang to) {
        return translateBatch(List.of(text == null ? "" : text), from, to).getFirst();
    }

    /** One translation per text, same order. Only strings missing from the cache reach the provider, once each. */
    public List<String> translateBatch(List<String> texts, Lang from, Lang to) {
        if (from == to) {
            return List.copyOf(texts);
        }
        Set<String> distinct = new LinkedHashSet<>();
        for (String text : texts) {
            if (text != null && !text.isBlank()) {
                distinct.add(text.trim());
            }
        }
        Map<String, String> hashes = new LinkedHashMap<>();
        distinct.forEach(text -> hashes.put(text, hash(text)));

        Map<String, String> cached = cache.find(hashes.values(), from, to);
        List<String> missing = new ArrayList<>();
        for (String text : distinct) {
            if (!cached.containsKey(hashes.get(text))) {
                missing.add(text);
            }
        }
        Map<String, String> translatedByText = new LinkedHashMap<>();
        if (!missing.isEmpty()) {
            List<String> translated = translator.translateBatch(missing, from, to);
            Map<String, String> toSave = new LinkedHashMap<>();
            for (int i = 0; i < missing.size(); i++) {
                translatedByText.put(missing.get(i), translated.get(i));
                toSave.put(hashes.get(missing.get(i)), translated.get(i));
            }
            cache.saveAll(toSave, from, to);
        }

        List<String> result = new ArrayList<>(texts.size());
        for (String text : texts) {
            if (text == null || text.isBlank()) {
                result.add(text);
                continue;
            }
            String trimmed = text.trim();
            String hit = cached.get(hashes.get(trimmed));
            result.add(hit != null ? hit : translatedByText.get(trimmed));
        }
        return result;
    }

    static String hash(String text) {
        return Hashes.sha256Hex(text);
    }
}
