package uz.karvon.common.domain;

import java.util.Optional;
import java.util.regex.Pattern;

/**
 * Uzbek mobile numbers in E.164 form: +998 followed by 9 digits.
 */
public final class PhoneNumbers {

    private static final Pattern UZ_E164 = Pattern.compile("^\\+998\\d{9}$");
    private static final Pattern SEPARATORS = Pattern.compile("[\\s()\\-]");

    private PhoneNumbers() {
    }

    /**
     * Removes spaces, dashes and brackets and adds a missing "+" before 998.
     * Returns empty if the result is not an Uzbek number.
     */
    public static Optional<String> normalize(String raw) {
        if (raw == null) {
            return Optional.empty();
        }
        String phone = SEPARATORS.matcher(raw.trim()).replaceAll("");
        if (phone.startsWith("998")) {
            phone = "+" + phone;
        }
        return UZ_E164.matcher(phone).matches() ? Optional.of(phone) : Optional.empty();
    }
}
