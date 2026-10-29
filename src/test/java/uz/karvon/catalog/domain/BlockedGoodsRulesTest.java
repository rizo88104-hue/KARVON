package uz.karvon.catalog.domain;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import uz.karvon.translation.domain.Lang;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class BlockedGoodsRulesTest {

    private final BlockedGoodsRules rules = new BlockedGoodsRules(List.of(
            new BlockedKeyword("香水", Lang.ZH, BlockedCategory.LIQUID),
            new BlockedKeyword("高仿", Lang.ZH, BlockedCategory.REPLICA),
            new BlockedKeyword("50000毫安", Lang.ZH, BlockedCategory.BATTERY),
            new BlockedKeyword("atir", Lang.UZ, BlockedCategory.LIQUID),
            new BlockedKeyword("replikasi", Lang.UZ, BlockedCategory.REPLICA),
            new BlockedKeyword("боевой нож", Lang.RU, BlockedCategory.WEAPON),
            new BlockedKeyword("духи", Lang.RU, BlockedCategory.LIQUID)));

    @ParameterizedTest(name = "{0}")
    @CsvSource(delimiter = '|', textBlock = """
            法式香水持久留香                          | LIQUID
            高仿名牌女包                              | REPLICA
            大容量充电宝50000毫安                     | BATTERY
            Atir 100 ml                               | LIQUID
            Mashhur brend sumkasining replikasi       | REPLICA
            Французские духи, стойкий аромат          | LIQUID
            Тактический БОЕВОЙ НОЖ                    | WEAPON
            """)
    void blocksTitlesWithKeywords(String title, BlockedCategory expected) {
        assertThat(rules.match(title)).hasValue(expected);
    }

    @ParameterizedTest
    @CsvSource(delimiter = '|', textBlock = """
            男士运动鞋透气网面跑步鞋
            Atirgul shaklidagi lampa
            Kitchen knife set
            Ножницы и нож для бумаги
            Powerbank 10000 mAh
            Духовой шкаф
            """)
    void allowsOtherTitles(String title) {
        assertThat(rules.match(title)).isEmpty();
    }

    @Test
    void checksEveryTextAndSkipsMissingOnes() {
        assertThat(rules.match(null, "男士运动鞋", "Fransuz atir")).hasValue(BlockedCategory.LIQUID);
        assertThat(rules.match((String) null)).isEmpty();
    }
}
