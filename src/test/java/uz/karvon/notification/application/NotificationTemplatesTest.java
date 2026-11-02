package uz.karvon.notification.application;

import org.junit.jupiter.api.Test;
import uz.karvon.common.infrastructure.I18nConfig;
import uz.karvon.user.domain.Language;

import java.util.List;
import java.util.Properties;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;

class NotificationTemplatesTest {

    private static final List<String> KEYS = List.of("order.paid", "order.purchased", "order.at_warehouse",
            "order.shipping_difference", "order.in_transit", "order.arrived", "order.out_of_stock",
            "order.price_changed", "order.shipping_refund", "order.difference_paid", "order.cancelled",
            "order.delivered", "telegram.linked", "telegram.link_invalid", "telegram.start_hint");

    private final NotificationTemplates templates = new NotificationTemplates(new I18nConfig().messageSource());

    @Test
    void uzbekTexts() {
        assertThat(templates.render("order.paid", List.of("K-1"), Language.UZ))
                .isEqualTo("Karvon: K-1 buyurtmangiz to'landi. Tez orada sotib olamiz.");
        assertThat(templates.render("order.at_warehouse", List.of("K-1", "1.25"), Language.UZ))
                .isEqualTo("Karvon: K-1 omborga keldi, vazni 1.25 kg.");
        assertThat(templates.render("order.shipping_difference", List.of("K-1", "32 000"), Language.UZ))
                .isEqualTo("Karvon: K-1 uchun yetkazish farqi 32 000 so'm. Kabinetda to'lang.");
        assertThat(templates.render("order.in_transit", List.of("K-1", "CN123UZ"), Language.UZ))
                .isEqualTo("Karvon: K-1 yo'lga chiqdi. Trek: CN123UZ.");
        assertThat(templates.render("order.arrived", List.of("K-1", "Chilonzor 9"), Language.UZ))
                .isEqualTo("Karvon: K-1 O'zbekistonga keldi. Chilonzor 9");
        assertThat(templates.render("order.arrived", List.of("K-1", ""), Language.UZ))
                .isEqualTo("Karvon: K-1 O'zbekistonga keldi.");
        assertThat(templates.render("order.out_of_stock", List.of("K-1"), Language.UZ)).endsWith("Kabinetda qaror qiling.");
    }

    @Test
    void russianTexts() {
        assertThat(templates.render("order.paid", List.of("K-1"), Language.RU))
                .isEqualTo("Karvon: заказ K-1 оплачен. Скоро мы его выкупим.");
        assertThat(templates.render("order.in_transit", List.of("K-1", "CN123UZ"), Language.RU))
                .isEqualTo("Karvon: заказ K-1 отправлен. Трек: CN123UZ.");
    }

    @Test
    void valuesAreNotParsedAsTemplates() {
        assertThat(templates.render("order.arrived", List.of("K-1", "Ofis 'Karvon' {0}"), Language.UZ))
                .isEqualTo("Karvon: K-1 O'zbekistonga keldi. Ofis 'Karvon' {0}");
    }

    @Test
    void everyTemplateExistsInBothLanguages() throws IOException {
        Properties uz = load("messages_uz.properties");
        Properties ru = load("messages_ru.properties");
        assertThat(KEYS).allSatisfy(key -> {
            assertThat(uz).containsKey(key);
            assertThat(ru).containsKey(key);
        });
    }

    private static Properties load(String name) throws IOException {
        Properties properties = new Properties();
        try (var reader = new InputStreamReader(NotificationTemplatesTest.class.getResourceAsStream("/" + name),
                StandardCharsets.UTF_8)) {
            properties.load(reader);
        }
        return properties;
    }
}
