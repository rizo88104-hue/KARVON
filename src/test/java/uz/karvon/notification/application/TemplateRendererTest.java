package uz.karvon.notification.application;

import org.junit.jupiter.api.Test;
import uz.karvon.common.infrastructure.I18nConfig;
import uz.karvon.user.domain.Language;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The real messages_uz / messages_ru files.
 */
class TemplateRendererTest {

    private final TemplateRenderer renderer = new TemplateRenderer(new I18nConfig().messageSource());

    @Test
    void uzbekTexts() {
        assertThat(renderer.render("order.paid", List.of("KRV-000042"), Language.UZ))
                .isEqualTo("E-Market: KRV-000042 buyurtmangiz to'landi. Tez orada sotib olamiz.");
        assertThat(renderer.render("order.at_warehouse", List.of("KRV-000042", "1.2"), Language.UZ))
                .isEqualTo("E-Market: KRV-000042 omborga keldi, vazni 1.2 kg.");
        assertThat(renderer.render("order.shipping_difference", List.of("KRV-000042", "32 000"), Language.UZ))
                .isEqualTo("E-Market: KRV-000042 uchun yetkazish farqi 32 000 so'm. Kabinetda to'lang.");
        assertThat(renderer.render("order.in_transit", List.of("KRV-000042", "CN123UZ"), Language.UZ))
                .isEqualTo("E-Market: KRV-000042 yo'lga chiqdi. Trek: CN123UZ.");
        assertThat(renderer.render("order.arrived", List.of("KRV-000042", "Chilonzor 9"), Language.UZ))
                .isEqualTo("E-Market: KRV-000042 O'zbekistonga keldi. Chilonzor 9");
    }

    @Test
    void russianTexts() {
        assertThat(renderer.render("order.paid", List.of("KRV-000042"), Language.RU))
                .isEqualTo("E-Market: заказ KRV-000042 оплачен. Скоро выкупим его.");
        assertThat(renderer.render("order.shipping_difference", List.of("KRV-000042", "32 000"), Language.RU))
                .isEqualTo("E-Market: доплата за доставку заказа KRV-000042: 32 000 сум. Оплатите в личном кабинете.");
    }

    @Test
    void emptyLastParameterLeavesNoTrailingSpace() {
        assertThat(renderer.render("order.arrived", List.of("KRV-000042", ""), Language.UZ))
                .isEqualTo("E-Market: KRV-000042 O'zbekistonga keldi.");
    }

    @Test
    void everyTemplateExistsInBothLanguages() {
        List<String> keys = List.of("order.paid", "order.purchased", "order.at_warehouse", "order.shipping_difference",
                "order.price_difference", "order.in_transit", "order.arrived", "order.out_of_stock",
                "order.price_changed", "payment.received", "telegram.start", "telegram.linked", "telegram.link_invalid");
        for (String key : keys) {
            String uz = renderer.render(key, List.of("A", "B"), Language.UZ);
            String ru = renderer.render(key, List.of("A", "B"), Language.RU);
            assertThat(uz).as(key).startsWith("E-Market:").doesNotContain("{");
            assertThat(ru).as(key).startsWith("E-Market:").doesNotContain("{").isNotEqualTo(uz);
        }
    }
}
