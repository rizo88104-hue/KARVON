package uz.karvon.catalog.domain;

/**
 * A name/value pair such as an attribute ("材质" = "棉") or a SKU property ("颜色分类" = "黑色"),
 * in Chinese and translated. The translated fields are null until the catalog service fills them.
 */
public record TranslatedPair(String nameOriginal, String valueOriginal, String name, String value) {

    public static TranslatedPair original(String name, String value) {
        return new TranslatedPair(name, value, null, null);
    }

    public TranslatedPair translated(String translatedName, String translatedValue) {
        return new TranslatedPair(nameOriginal, valueOriginal, translatedName, translatedValue);
    }
}
