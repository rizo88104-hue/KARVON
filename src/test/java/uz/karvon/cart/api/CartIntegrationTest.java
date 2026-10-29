package uz.karvon.cart.api;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.web.servlet.assertj.MockMvcTester;
import org.springframework.test.web.servlet.assertj.MvcTestResult;
import uz.karvon.AbstractIntegrationTest;
import uz.karvon.TestLogins;
import uz.karvon.catalog.domain.ProductDetail;
import uz.karvon.catalog.domain.Sku;
import uz.karvon.catalog.infrastructure.FakeTaobaoProvider;
import uz.karvon.translation.infrastructure.FakeTranslator;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.doReturn;
import static uz.karvon.TestLogins.accessToken;
import static uz.karvon.TestLogins.bearer;
import static uz.karvon.TestLogins.newPhone;
import static uz.karvon.TestLogins.read;

/**
 * The cart with the fake provider. The provider is a spy (same beans as CatalogIntegrationTest, so the
 * Spring context is shared) to simulate price and stock changes on Taobao.
 */
class CartIntegrationTest extends AbstractIntegrationTest {

    /** 男士运动鞋, shop 运动鞋专营店, 129 CNY, 0.9 kg. SKU 001 stock 73, 002 stock 102, 003 stock 0. */
    private static final String SNEAKERS = "680000001117";
    /** 女士小白鞋, shop 小白鞋工厂店, 89 CNY, 0.8 kg. SKU 001 stock 126, 002 stock 155. */
    private static final String WHITE_SHOES = "680000002234";

    @Autowired
    private MockMvcTester mvc;

    @MockitoSpyBean
    private FakeTaobaoProvider provider;

    @MockitoSpyBean
    private FakeTranslator translator;

    @Autowired
    private StringRedisTemplate redis;

    private String token;

    @BeforeEach
    void login() {
        token = accessToken(mvc, newPhone());
    }

    @AfterEach
    void forgetChangedProducts() {
        // refresh-prices stores what the (stubbed) provider answered; other tests expect the original data.
        redis.delete(List.of("catalog:product:" + SNEAKERS, "catalog:product:" + WHITE_SHOES));
    }

    @Test
    void twoShopsAndTotalsOfSelectedItemsOnly() {
        assertThat(add(SNEAKERS, "002", 2, null)).hasStatus(HttpStatus.CREATED);
        MvcTestResult cart = add(WHITE_SHOES, "001", 1, "Qutisi bilan");

        assertThat(cart).bodyJson().extractingPath("$.shops.length()").isEqualTo(2);
        assertThat(cart).bodyJson().extractingPath("$.shops[0].shopName").isEqualTo("运动鞋专营店");
        assertThat(cart).bodyJson().extractingPath("$.shops[1].shopName").isEqualTo("小白鞋工厂店");
        // 129 x 2, 0.9 kg: 459 200 + 45 900 + 144 000
        assertThat(cart).bodyJson().extractingPath("$.shops[0].items[0].price.totalUzs").isEqualTo(649100);
        // 89, 0.8 kg: 158 400 + 15 800 + 64 000
        assertThat(cart).bodyJson().extractingPath("$.shops[1].items[0].price.totalUzs").isEqualTo(238200);
        assertThat(cart).bodyJson().extractingPath("$.totals.totalUzs").isEqualTo(887300);
        assertThat(cart).bodyJson().extractingPath("$.totals.quantity").isEqualTo(3);
        assertThat(cart).bodyJson().extractingPath("$.itemCount").isEqualTo(2);

        String whiteShoesLine = read(cart, "$.shops[1].items[0].id");
        MvcTestResult unselected = patch("/api/v1/cart/items/" + whiteShoesLine, "{\"selected\":false}");
        assertThat(unselected).bodyJson().extractingPath("$.totals.totalUzs").isEqualTo(649100);
        assertThat(unselected).bodyJson().extractingPath("$.shops[1].totals.totalUzs").isEqualTo(0);
        assertThat(unselected).bodyJson().extractingPath("$.selectedCount").isEqualTo(1);

        assertThat(patch("/api/v1/cart/select-all", "{\"selected\":false}"))
                .bodyJson().extractingPath("$.totals.totalUzs").isEqualTo(0);
        assertThat(patch("/api/v1/cart/select-all", "{\"selected\":true}"))
                .bodyJson().extractingPath("$.totals.totalUzs").isEqualTo(887300);
    }

    @Test
    void sameSkuIsMergedAndKeepsTranslatedSnapshot() {
        add(SNEAKERS, "001", 1, null);
        MvcTestResult cart = add(SNEAKERS, "001", 2, "  42 o'lcham bo'lsin ");

        assertThat(cart).bodyJson().extractingPath("$.itemCount").isEqualTo(1);
        assertThat(cart).bodyJson().extractingPath("$.shops[0].items[0].quantity").isEqualTo(3);
        assertThat(cart).bodyJson().extractingPath("$.shops[0].items[0].comment").isEqualTo("42 o'lcham bo'lsin");
        assertThat(cart).bodyJson().extractingPath("$.shops[0].items[0].title")
                .isEqualTo("Erkaklar sport krossovkasi, nafas oladigan to'r matoli");
        assertThat(cart).bodyJson().extractingPath("$.shops[0].items[0].skuProperties[0].name").isEqualTo("Rang");
        assertThat(cart).bodyJson().extractingPath("$.shops[0].items[0].skuProperties[0].value").isEqualTo("Qora");
        assertThat(cart).bodyJson().extractingPath("$.shops[0].items[0].priceCny").satisfies(value -> assertThat(new BigDecimal(value.toString())).isEqualByComparingTo("129.0"));

        // Another SKU of the same product is a separate line.
        assertThat(add(SNEAKERS, "002", 1, null)).bodyJson().extractingPath("$.itemCount").isEqualTo(2);
    }

    @Test
    void quantityLimits() {
        MvcTestResult tooMany = add(SNEAKERS, "002", 100, null);
        assertThat(tooMany).hasStatus(HttpStatus.BAD_REQUEST);
        assertThat(tooMany).bodyJson().extractingPath("$.code").isEqualTo("VALIDATION_ERROR");

        add(SNEAKERS, "002", 60, null);
        MvcTestResult merged = add(SNEAKERS, "002", 40, null);
        assertThat(merged).hasStatus(HttpStatus.BAD_REQUEST);
        assertThat(merged).bodyJson().extractingPath("$.code").isEqualTo("CART_LIMIT");
        assertThat(merged).bodyJson().extractingPath("$.detail").isEqualTo("Bitta tovardan ko'pi bilan 99 dona olish mumkin");

        String line = read(get(), "$.shops[0].items[0].id");
        assertThat(patch("/api/v1/cart/items/" + line, "{\"quantity\":0}")).hasStatus(HttpStatus.BAD_REQUEST);
        assertThat(patch("/api/v1/cart/items/" + line, "{\"quantity\":99}"))
                .bodyJson().extractingPath("$.shops[0].items[0].quantity").isEqualTo(99);
    }

    @Test
    void stockSkuAndBlockedChecksOnAdd() {
        MvcTestResult soldOut = add(SNEAKERS, "003", 1, null);
        assertThat(soldOut).hasStatus(HttpStatus.CONFLICT);
        assertThat(soldOut).bodyJson().extractingPath("$.code").isEqualTo("OUT_OF_STOCK");

        MvcTestResult notEnough = add(SNEAKERS, "001", 74, null);
        assertThat(notEnough).bodyJson().extractingPath("$.code").isEqualTo("OUT_OF_STOCK");
        assertThat(notEnough).bodyJson().extractingPath("$.detail").isEqualTo("Omborda faqat 73 dona qoldi");

        MvcTestResult noSku = add(SNEAKERS, "999", 1, null);
        assertThat(noSku).hasStatus(HttpStatus.NOT_FOUND);
        assertThat(noSku).bodyJson().extractingPath("$.code").isEqualTo("SKU_NOT_FOUND");

        MvcTestResult blocked = mvc.post().uri("/api/v1/cart/items").header(HttpHeaders.AUTHORIZATION, bearer(token))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"externalProductId\":\"%s\",\"skuId\":\"%s\"}".formatted(perfumeId(), perfumeId() + "001"))
                .exchange();
        assertThat(blocked).hasStatus(HttpStatus.UNPROCESSABLE_CONTENT);
        assertThat(blocked).bodyJson().extractingPath("$.code").isEqualTo("PRODUCT_BLOCKED");

        assertThat(get()).bodyJson().extractingPath("$.itemCount").isEqualTo(0);
    }

    @Test
    void atMostFiftyLines() {
        List<String[]> skus = new ArrayList<>();
        for (String id : provider.productIds()) {
            ProductDetail product = provider.getProduct(id);
            if (product.titleOriginal().matches(".*(香水|高仿|50000毫安).*")) {
                continue;
            }
            for (Sku sku : product.skus()) {
                if (sku.stock() > 0 && skus.size() < 51) {
                    skus.add(new String[]{id, sku.skuId()});
                }
            }
        }
        for (int i = 0; i < 50; i++) {
            assertThat(addSku(skus.get(i)[0], skus.get(i)[1])).as("line " + i).hasStatus(HttpStatus.CREATED);
        }

        MvcTestResult fiftyFirst = addSku(skus.get(50)[0], skus.get(50)[1]);

        assertThat(fiftyFirst).hasStatus(HttpStatus.BAD_REQUEST);
        assertThat(fiftyFirst).bodyJson().extractingPath("$.code").isEqualTo("CART_LIMIT");
        // Adding to a line that is already there still works.
        assertThat(addSku(skus.getFirst()[0], skus.getFirst()[1])).hasStatus(HttpStatus.CREATED);
    }

    @Test
    void refreshDetectsPriceChangesAndMissingStock() {
        add(WHITE_SHOES, "001", 1, null);
        add(WHITE_SHOES, "002", 2, null);
        MvcTestResult unchanged = post("/api/v1/cart/refresh-prices");
        assertThat(unchanged).hasStatusOk();
        assertThat(unchanged).bodyJson().extractingPath("$.shops[0].items[0].priceChanged").isEqualTo(false);

        // On Taobao: SKU 001 now costs 99 instead of 89, SKU 002 is sold out.
        ProductDetail original = provider.getProduct(WHITE_SHOES);
        doReturn(changed(original, WHITE_SHOES + "001", new BigDecimal("99.00"), WHITE_SHOES + "002"))
                .when(provider).getProduct(WHITE_SHOES);

        MvcTestResult refreshed = post("/api/v1/cart/refresh-prices");

        assertThat(refreshed).hasStatusOk();
        assertThat(refreshed).bodyJson().extractingPath("$.shops[0].items[0].priceChanged").isEqualTo(true);
        assertThat(refreshed).bodyJson().extractingPath("$.shops[0].items[0].previousPriceCny").satisfies(value -> assertThat(new BigDecimal(value.toString())).isEqualByComparingTo("89.0"));
        assertThat(refreshed).bodyJson().extractingPath("$.shops[0].items[0].priceCny").satisfies(value -> assertThat(new BigDecimal(value.toString())).isEqualByComparingTo("99.0"));
        // 99 CNY, 0.8 kg: 176 200 + 17 600 + 64 000
        assertThat(refreshed).bodyJson().extractingPath("$.shops[0].items[0].price.totalUzs").isEqualTo(257800);
        assertThat(refreshed).bodyJson().extractingPath("$.shops[0].items[1].outOfStock").isEqualTo(true);
        assertThat(refreshed).bodyJson().extractingPath("$.shops[0].items[1].selected").isEqualTo(false);
        assertThat(refreshed).bodyJson().extractingPath("$.totals.totalUzs").isEqualTo(257800);
        assertThat(refreshed).bodyJson().extractingPath("$.totals.lines").isEqualTo(1);

        // Selecting the sold-out line again does not put it into the totals.
        String soldOutLine = read(refreshed, "$.shops[0].items[1].id");
        assertThat(patch("/api/v1/cart/items/" + soldOutLine, "{\"selected\":true}"))
                .bodyJson().extractingPath("$.totals.totalUzs").isEqualTo(257800);
    }

    @Test
    void deleteLinesAndOnlyOwnLinesAreVisible() {
        add(SNEAKERS, "001", 1, null);
        MvcTestResult cart = add(WHITE_SHOES, "001", 1, null);
        String sneakersLine = read(cart, "$.shops[0].items[0].id");
        patch("/api/v1/cart/items/" + sneakersLine, "{\"selected\":false}");

        String otherCustomer = accessToken(mvc, newPhone());
        MvcTestResult foreign = mvc.delete().uri("/api/v1/cart/items/{id}", sneakersLine)
                .header(HttpHeaders.AUTHORIZATION, bearer(otherCustomer)).exchange();
        assertThat(foreign).hasStatus(HttpStatus.NOT_FOUND);
        assertThat(foreign).bodyJson().extractingPath("$.code").isEqualTo("CART_ITEM_NOT_FOUND");
        assertThat(foreign).bodyJson().extractingPath("$.detail").isEqualTo("Savatda bunday tovar yo'q");

        MvcTestResult afterSelectedDeleted = request(mvc.delete().uri("/api/v1/cart/items?selected=true"));
        assertThat(afterSelectedDeleted).bodyJson().extractingPath("$.itemCount").isEqualTo(1);
        assertThat(afterSelectedDeleted).bodyJson().extractingPath("$.shops[0].items[0].externalProductId").isEqualTo(SNEAKERS);

        assertThat(request(mvc.delete().uri("/api/v1/cart/items/{id}", sneakersLine)))
                .bodyJson().extractingPath("$.itemCount").isEqualTo(0);
    }

    @Test
    void onlyCustomersHaveACart() {
        assertThat(mvc.get().uri("/api/v1/cart")).hasStatus(HttpStatus.UNAUTHORIZED);
        // The admin from ADMIN_PHONE has only the ADMIN role.
        assertThat(mvc.get().uri("/api/v1/cart").header(HttpHeaders.AUTHORIZATION, bearer(TestLogins.adminToken(mvc))))
                .hasStatus(HttpStatus.FORBIDDEN);
    }

    private String perfumeId() {
        return provider.productIds().stream()
                .filter(id -> provider.getProduct(id).titleOriginal().contains("香水"))
                .findFirst().orElseThrow();
    }

    /** The same product with one SKU at a new price and another SKU sold out. */
    private static ProductDetail changed(ProductDetail product, String repricedSku, BigDecimal newPrice, String soldOutSku) {
        List<Sku> skus = product.skus().stream()
                .map(sku -> sku.skuId().equals(repricedSku)
                        ? new Sku(sku.skuId(), sku.properties(), newPrice, sku.stock(), sku.imageUrl())
                        : sku.skuId().equals(soldOutSku)
                        ? new Sku(sku.skuId(), sku.properties(), sku.priceCny(), 0, sku.imageUrl())
                        : sku)
                .toList();
        return new ProductDetail(product.externalId(), product.titleOriginal(), null, product.mainImageUrl(),
                product.images(), product.priceCny(), product.salesCount(), product.shop(), product.attributes(), skus,
                product.chinaShippingCny(), product.weightKg());
    }

    private MvcTestResult add(String productId, String skuSuffix, int quantity, String comment) {
        String body = comment == null
                ? "{\"externalProductId\":\"%s\",\"skuId\":\"%s\",\"quantity\":%d}".formatted(productId, productId + skuSuffix, quantity)
                : "{\"externalProductId\":\"%s\",\"skuId\":\"%s\",\"quantity\":%d,\"comment\":\"%s\"}"
                .formatted(productId, productId + skuSuffix, quantity, comment);
        return request(mvc.post().uri("/api/v1/cart/items").contentType(MediaType.APPLICATION_JSON).content(body));
    }

    private MvcTestResult addSku(String productId, String skuId) {
        return request(mvc.post().uri("/api/v1/cart/items").contentType(MediaType.APPLICATION_JSON)
                .content("{\"externalProductId\":\"%s\",\"skuId\":\"%s\"}".formatted(productId, skuId)));
    }

    private MvcTestResult get() {
        return request(mvc.get().uri("/api/v1/cart"));
    }

    private MvcTestResult post(String uri) {
        return request(mvc.post().uri(uri));
    }

    private MvcTestResult patch(String uri, String body) {
        return request(mvc.patch().uri(uri).contentType(MediaType.APPLICATION_JSON).content(body));
    }

    private MvcTestResult request(MockMvcTester.MockMvcRequestBuilder request) {
        return request.header(HttpHeaders.AUTHORIZATION, bearer(token)).exchange();
    }
}
