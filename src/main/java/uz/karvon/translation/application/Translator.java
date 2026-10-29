package uz.karvon.translation.application;

import uz.karvon.translation.domain.Lang;

import java.util.List;

/**
 * A machine translation provider. Other modules use {@link TranslationService}, which caches every string.
 * Throws {@link TranslationUnavailableException} when the provider fails.
 */
public interface Translator {

    String translate(String text, Lang from, Lang to);

    /** Returns one translation per text, in the same order. */
    List<String> translateBatch(List<String> texts, Lang from, Lang to);
}
