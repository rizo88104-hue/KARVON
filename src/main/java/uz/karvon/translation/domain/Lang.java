package uz.karvon.translation.domain;

import java.util.Locale;

/**
 * Languages we translate between. Customers use UZ or RU; Taobao is ZH.
 */
public enum Lang {
    UZ("uz"), RU("ru"), ZH("zh");

    private final String code;

    Lang(String code) {
        this.code = code;
    }

    /** ISO 639-1 code, as Google and Yandex expect it. */
    public String code() {
        return code;
    }

    /** The customer's language for a request locale: RU for Russian, otherwise UZ. */
    public static Lang ofCustomer(Locale locale) {
        return locale != null && "ru".equals(locale.getLanguage()) ? RU : UZ;
    }
}
