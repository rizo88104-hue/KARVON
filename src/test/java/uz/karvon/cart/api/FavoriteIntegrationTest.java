package uz.karvon.cart.api;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.web.servlet.assertj.MockMvcTester;
import org.springframework.test.web.servlet.assertj.MvcTestResult;
import uz.karvon.AbstractIntegrationTest;
import uz.karvon.cart.domain.Favorite;
import uz.karvon.catalog.infrastructure.FakeTaobaoProvider;
import uz.karvon.translation.infrastructure.FakeTranslator;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static uz.karvon.TestLogins.accessToken;
import static uz.karvon.TestLogins.bearer;
import static uz.karvon.TestLogins.newPhone;
import static uz.karvon.TestLogins.read;

/**
 * Favorites ("Sevimlilar") with the fake catalog. Same spy beans as CatalogIntegrationTest, so the context is shared.
 */
class FavoriteIntegrationTest extends AbstractIntegrationTest {

    /** 男士运动鞋透气网面跑步鞋, 129 CNY (old price 168). */
    private static final String SNEAKERS = "680000001117";
    /** 女士小白鞋百搭运动鞋, 89 CNY. */
    private static final String WHITE_SHOES = "680000002234";
    /** 法式香水持久留香: perfume is a blocked keyword. */
    private static final String PERFUME = "680000053616";

    @Autowired
    private MockMvcTester mvc;

    @MockitoSpyBean
    private FakeTaobaoProvider provider;

    @MockitoSpyBean
    private FakeTranslator translator;

    @Autowired
    private JdbcTemplate jdbc;

    private String token;

    @BeforeEach
    void login() {
        token = accessToken(mvc, newPhone());
    }

    @Test
    void savedProductsAreCardsInTheCustomersLanguageNewestFirst() {
        assertThat(put(SNEAKERS)).hasStatus(HttpStatus.NO_CONTENT);
        assertThat(put(WHITE_SHOES)).hasStatus(HttpStatus.NO_CONTENT);
        assertThat(put(SNEAKERS)).as("saving twice changes nothing").hasStatus(HttpStatus.NO_CONTENT);

        MvcTestResult ids = get("/api/v1/me/favorites/ids", "uz");
        assertThat(ids).bodyJson().extractingPath("$").asArray().containsExactly(WHITE_SHOES, SNEAKERS);

        MvcTestResult uz = get("/api/v1/me/favorites", "uz");
        assertThat(uz).hasStatusOk();
        assertThat(uz).bodyJson().extractingPath("$.length()").isEqualTo(2);
        assertThat(uz).bodyJson().extractingPath("$[1].externalId").isEqualTo(SNEAKERS);
        assertThat(uz).bodyJson().extractingPath("$[1].title")
                .isEqualTo("Erkaklar sport krossovkasi, nafas oladigan to'r matoli");
        assertThat(uz).bodyJson().extractingPath("$[1].priceCny").isEqualTo(129);
        assertThat(uz).bodyJson().extractingPath("$[1].originalPriceCny").isEqualTo(168);
        String searchPrice = read(mvc.get().uri("/api/v1/catalog/search?q=运动鞋").exchange(),
                "$.content[?(@.externalId == '" + SNEAKERS + "')].priceUzs");
        assertThat(read(uz, "$[1].priceUzs")).as("the same price as in the search").isEqualTo(searchPrice
                .replaceAll("[\\[\\]]", ""));

        MvcTestResult ru = get("/api/v1/me/favorites", "ru");
        assertThat(ru).bodyJson().extractingPath("$[1].title").isEqualTo("Мужские спортивные кроссовки, дышащая сетка");
    }

    @Test
    void removingIsIdempotentAndListsArePerCustomer() {
        put(SNEAKERS);
        String other = accessToken(mvc, newPhone());

        assertThat(mvc.get().uri("/api/v1/me/favorites/ids").header(HttpHeaders.AUTHORIZATION, bearer(other)))
                .bodyJson().extractingPath("$").asArray().isEmpty();
        assertThat(delete(SNEAKERS)).hasStatus(HttpStatus.NO_CONTENT);
        assertThat(delete(SNEAKERS)).hasStatus(HttpStatus.NO_CONTENT);
        assertThat(get("/api/v1/me/favorites/ids", "uz")).bodyJson().extractingPath("$").asArray().isEmpty();
    }

    @Test
    void unknownAndBlockedProductsCannotBeSaved() {
        MvcTestResult unknown = put("680000000001");
        assertThat(unknown).hasStatus(HttpStatus.NOT_FOUND);
        assertThat(unknown).bodyJson().extractingPath("$.code").isEqualTo("PRODUCT_NOT_FOUND");

        MvcTestResult blocked = put(PERFUME);
        assertThat(blocked).hasStatus(HttpStatus.UNPROCESSABLE_CONTENT);
        assertThat(blocked).bodyJson().extractingPath("$.code").isEqualTo("PRODUCT_BLOCKED");
    }

    @Test
    void atMostTwoHundredFavorites() {
        UUID userId = UUID.fromString(read(mvc.get().uri("/api/v1/me").header(HttpHeaders.AUTHORIZATION, bearer(token))
                .exchange(), "$.id"));
        for (int i = 0; i < Favorite.MAX_PER_USER; i++) {
            jdbc.update("""
                    INSERT INTO favorites (id, user_id, external_product_id, title_original, price_cny, sales_count,
                                           created_at, updated_at, version)
                    VALUES (gen_random_uuid(), ?, ?, '测试', 1, 0, now(), now(), 0)""", userId, "69" + (1000000000 + i));
        }

        MvcTestResult full = put(SNEAKERS);
        assertThat(full).hasStatus(HttpStatus.BAD_REQUEST);
        assertThat(full).bodyJson().extractingPath("$.code").isEqualTo("FAVORITES_LIMIT");
        assertThat(full).bodyJson().extractingPath("$.detail").isEqualTo("Sevimlilarda ko'pi bilan 200 ta mahsulot bo'lishi mumkin");
    }

    @Test
    void guestFavoritesAreMergedAfterLoginSkippingWhatCannotBeSaved() {
        put(SNEAKERS);

        MvcTestResult merged = mvc.post().uri("/api/v1/me/favorites/merge").header(HttpHeaders.AUTHORIZATION, bearer(token))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"externalIds\":[\"%s\",\"680000000001\",\"%s\",\"%s\"]}".formatted(WHITE_SHOES, PERFUME, SNEAKERS))
                .exchange();

        assertThat(merged).hasStatusOk();
        assertThat(merged).bodyJson().extractingPath("$").asArray().containsExactly(WHITE_SHOES, SNEAKERS);
        assertThat(mvc.post().uri("/api/v1/me/favorites/merge").header(HttpHeaders.AUTHORIZATION, bearer(token))
                .contentType(MediaType.APPLICATION_JSON).content("{}")).hasStatus(HttpStatus.BAD_REQUEST);
    }

    @Test
    void favoritesNeedLogin() {
        assertThat(mvc.get().uri("/api/v1/me/favorites")).hasStatus(HttpStatus.UNAUTHORIZED);
    }

    private MvcTestResult put(String externalId) {
        return mvc.put().uri("/api/v1/me/favorites/{id}", externalId).header(HttpHeaders.AUTHORIZATION, bearer(token))
                .exchange();
    }

    private MvcTestResult delete(String externalId) {
        return mvc.delete().uri("/api/v1/me/favorites/{id}", externalId).header(HttpHeaders.AUTHORIZATION, bearer(token))
                .exchange();
    }

    private MvcTestResult get(String uri, String lang) {
        return mvc.get().uri(uri).header(HttpHeaders.AUTHORIZATION, bearer(token))
                .header(HttpHeaders.ACCEPT_LANGUAGE, lang).exchange();
    }
}
