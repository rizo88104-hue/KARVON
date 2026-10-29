package uz.karvon.catalog.domain;

import uz.karvon.translation.domain.Lang;

import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;
import java.util.regex.Pattern;

/**
 * Decides whether a product is blocked by looking for blocked keywords in its texts (Chinese and translated title).
 * Chinese keywords match anywhere (Chinese has no spaces); Uzbek and Russian keywords match whole words only,
 * so "atir" (perfume) does not block "atirgul" (rose). Case is ignored.
 */
public final class BlockedGoodsRules {

    private final List<Rule> rules;

    public BlockedGoodsRules(List<BlockedKeyword> keywords) {
        this.rules = keywords.stream()
                .map(keyword -> new Rule(keyword.getKeyword().toLowerCase(Locale.ROOT), keyword.getLang(), keyword.getCategory()))
                .toList();
    }

    /** The category of the first matching keyword, if any text contains one. Null texts are skipped. */
    public Optional<BlockedCategory> match(String... texts) {
        for (String text : texts) {
            if (text == null || text.isBlank()) {
                continue;
            }
            String lower = text.toLowerCase(Locale.ROOT);
            for (Rule rule : rules) {
                if (rule.matches(lower)) {
                    return Optional.of(rule.category());
                }
            }
        }
        return Optional.empty();
    }

    private record Rule(String keyword, Lang lang, BlockedCategory category, Pattern wholeWord) {

        Rule(String keyword, Lang lang, BlockedCategory category) {
            this(keyword, lang, category, lang == Lang.ZH ? null
                    : Pattern.compile("(?<![\\p{L}\\p{N}'])" + Pattern.quote(keyword) + "(?![\\p{L}\\p{N}'])"));
        }

        boolean matches(String lowerText) {
            Objects.requireNonNull(lowerText);
            return wholeWord == null ? lowerText.contains(keyword) : wholeWord.matcher(lowerText).find();
        }
    }
}
