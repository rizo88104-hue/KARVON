package uz.karvon.catalog.domain;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.net.URI;

import static org.assertj.core.api.Assertions.assertThat;

class TaobaoLinksTest {

    private static final String ID = "681234567890";

    @ParameterizedTest
    @ValueSource(strings = {
            "https://item.taobao.com/item.htm?id=681234567890",
            "http://item.taobao.com/item.htm?id=681234567890",
            "item.taobao.com/item.htm?id=681234567890",
            "https://item.taobao.com/item.htm?spm=a1z10.1-c.w4004-1.2.6d3c&id=681234567890&ns=1&abbucket=7",
            "https://item.taobao.com/item.htm?ft=t&id=681234567890#detail",
            "https://ITEM.TAOBAO.COM/item.htm?id=681234567890",
            "https://detail.tmall.com/item.htm?id=681234567890&skuId=5012345678901",
            "https://detail.m.tmall.com/item.htm?id=681234567890",
            "https://chaoshi.detail.tmall.com/item.htm?id=681234567890",
            "https://detail.tmall.hk/hk/item.htm?id=681234567890",
            "https://h5.m.taobao.com/awp/core/detail.htm?id=681234567890",
            "https://m.intl.taobao.com/detail/detail.html?id=681234567890",
            "https://main.m.taobao.com/security-h5-detail/home?itemId=681234567890",
            "https://world.taobao.com/item/681234567890.htm",
            "https://a.m.taobao.com/i681234567890.htm",
            "Mana shu tovar: https://item.taobao.com/item.htm?id=681234567890 olib bering",
            "  https://detail.tmall.com/item.htm?id=681234567890\n"
    })
    void readsItemIdFromEveryLinkForm(String pasted) {
        assertThat(TaobaoLinks.itemId(pasted)).hasValue(ID);
        assertThat(TaobaoLinks.shortLink(pasted)).isEmpty();
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "https://m.tb.cn/h.5AbCdEf?tk=xYz123",
            "m.tb.cn/h.5AbCdEf",
            "https://tb.cn/h.5AbCdEf",
            "【淘宝】限时特惠 https://m.tb.cn/h.5AbCdEf?tk=xYz123 CZ3457 「男士运动鞋透气网面跑步鞋」点击链接直接打开"
    })
    void recognizesShortLinksThatMustBeOpened(String pasted) {
        assertThat(TaobaoLinks.itemId(pasted)).isEmpty();
        assertThat(TaobaoLinks.shortLink(pasted)).get()
                .satisfies(uri -> assertThat(uri.getHost()).endsWith("tb.cn"))
                .satisfies(uri -> assertThat(uri.getPath()).isEqualTo("/h.5AbCdEf"));
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "",
            "krossovka",
            "https://www.amazon.com/dp/B000123456",
            "https://item.taobao.com/item.htm",
            "https://item.taobao.com/item.htm?id=abc",
            "https://item.taobao.com/item.htm?id=123",
            "https://item.taobao.com.evil.com/item.htm?id=681234567890",
            "https://evil.com/?id=681234567890&from=item.taobao.com",
            "https://faketaobao.com/item.htm?id=681234567890",
            "https://m.tb.cn/",
            "https://shop123.taobao.com/category.htm?catId=681234567890"
    })
    void rejectsEverythingElse(String pasted) {
        assertThat(TaobaoLinks.itemId(pasted)).isEmpty();
        assertThat(TaobaoLinks.shortLink(pasted)).isEmpty();
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "https://item.taobao.com/item.htm?id=681234567890",
            "https://detail.tmall.com/item.htm?spm=x&id=681234567890"
    })
    void readsIdFromRedirectTarget(String target) {
        assertThat(TaobaoLinks.idOf(URI.create(target))).hasValue(ID);
    }
}
