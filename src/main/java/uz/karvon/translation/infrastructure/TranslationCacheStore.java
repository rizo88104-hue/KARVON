package uz.karvon.translation.infrastructure;

import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;
import uz.karvon.translation.domain.Lang;

import java.util.Collection;
import java.util.HashMap;
import java.util.Map;

/**
 * The translation_cache table. Plain JDBC: rows are written with ON CONFLICT DO NOTHING,
 * so two requests translating the same string at once do not fail.
 */
@Repository
@RequiredArgsConstructor
public class TranslationCacheStore {

    private final NamedParameterJdbcTemplate jdbc;

    /** source hash -> translated text, for the hashes that are cached. */
    public Map<String, String> find(Collection<String> hashes, Lang from, Lang to) {
        Map<String, String> found = new HashMap<>();
        if (hashes.isEmpty()) {
            return found;
        }
        jdbc.query("""
                        SELECT source_hash, text FROM translation_cache
                        WHERE from_lang = :from AND to_lang = :to AND source_hash IN (:hashes)""",
                new MapSqlParameterSource()
                        .addValue("from", from.name())
                        .addValue("to", to.name())
                        .addValue("hashes", hashes),
                row -> {
                    found.put(row.getString("source_hash"), row.getString("text"));
                });
        return found;
    }

    /** source hash -> translated text. */
    public void saveAll(Map<String, String> translations, Lang from, Lang to) {
        MapSqlParameterSource[] rows = translations.entrySet().stream()
                .map(entry -> new MapSqlParameterSource()
                        .addValue("hash", entry.getKey())
                        .addValue("from", from.name())
                        .addValue("to", to.name())
                        .addValue("text", entry.getValue()))
                .toArray(MapSqlParameterSource[]::new);
        jdbc.batchUpdate("""
                INSERT INTO translation_cache (source_hash, from_lang, to_lang, text)
                VALUES (:hash, :from, :to, :text)
                ON CONFLICT DO NOTHING""", rows);
    }
}
