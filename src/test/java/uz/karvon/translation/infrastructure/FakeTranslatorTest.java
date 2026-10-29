package uz.karvon.translation.infrastructure;

import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;
import uz.karvon.translation.domain.Lang;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class FakeTranslatorTest {

    private final FakeTranslator translator = new FakeTranslator(JsonMapper.builder().build());

    @Test
    void translatesSearchWordsToChinese() {
        assertThat(translator.translate("krossovka", Lang.UZ, Lang.ZH)).isEqualTo("运动鞋");
        assertThat(translator.translate("  Krossovka ", Lang.UZ, Lang.ZH)).isEqualTo("运动鞋");
        assertThat(translator.translate("кроссовки", Lang.RU, Lang.ZH)).isEqualTo("运动鞋");
        assertThat(translator.translate("ko‘ylak", Lang.UZ, Lang.ZH)).isEqualTo("连衣裙");
    }

    @Test
    void translatesKnownWordsOfAPhrase() {
        assertThat(translator.translate("qora krossovka", Lang.UZ, Lang.ZH)).isEqualTo("黑色 运动鞋");
        assertThat(translator.translate("qora noma'lum krossovka", Lang.UZ, Lang.ZH)).isEqualTo("黑色 运动鞋");
    }

    @Test
    void translatesFakeProductTextsBack() {
        assertThat(translator.translateBatch(List.of("男士运动鞋透气网面跑步鞋", "黑色", "颜色分类"), Lang.ZH, Lang.UZ))
                .containsExactly("Erkaklar sport krossovkasi, nafas oladigan to'r matoli", "Qora", "Rang");
        assertThat(translator.translate("黑色", Lang.ZH, Lang.RU)).isEqualTo("Чёрный");
    }

    @Test
    void unknownTextIsReturnedUnchanged() {
        assertThat(translator.translate("noma'lum so'z", Lang.UZ, Lang.ZH)).isEqualTo("noma'lum so'z");
        assertThat(translator.translate("未知的商品", Lang.ZH, Lang.UZ)).isEqualTo("未知的商品");
    }
}
