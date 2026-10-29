package uz.karvon.translation.infrastructure;

import org.springframework.context.annotation.Profile;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.json.JsonMapper;
import uz.karvon.translation.application.Translator;
import uz.karvon.translation.domain.Lang;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Dev and test: a small dictionary from fake-translation/dictionary.json (search words uz/ru to zh, and the
 * fake products' titles, colours and attributes zh to uz/ru). A whole text is looked up first; a uz/ru text
 * is then translated word by word (known words only). Anything else is returned unchanged.
 */
@Component
@Profile({"dev", "test"})
public class FakeTranslator implements Translator {

    private final Map<String, Map<String, String>> dictionary;

    public FakeTranslator(JsonMapper json) {
        try (InputStream in = new ClassPathResource("fake-translation/dictionary.json").getInputStream()) {
            this.dictionary = json.readValue(in, new TypeReference<>() {
            });
        } catch (IOException ex) {
            throw new UncheckedIOException(ex);
        }
    }

    @Override
    public String translate(String text, Lang from, Lang to) {
        Map<String, String> words = dictionary.getOrDefault(from.code() + ">" + to.code(), Map.of());
        String key = normalize(text, from);
        String whole = words.get(key);
        if (whole != null) {
            return whole;
        }
        if (from == Lang.ZH) {
            return text;
        }
        List<String> known = new ArrayList<>();
        for (String word : key.split("\\s+")) {
            String translated = words.get(word);
            if (translated != null) {
                known.add(translated);
            }
        }
        return known.isEmpty() ? text : String.join(" ", known);
    }

    @Override
    public List<String> translateBatch(List<String> texts, Lang from, Lang to) {
        return texts.stream().map(text -> translate(text, from, to)).toList();
    }

    /** Lower case and one kind of apostrophe for uz/ru (o‘, oʻ and o' are the same letter). */
    private static String normalize(String text, Lang from) {
        String trimmed = text.trim();
        if (from == Lang.ZH) {
            return trimmed;
        }
        return trimmed.toLowerCase(Locale.ROOT).replace('‘', '\'').replace('ʻ', '\'').replace('’', '\'');
    }
}
