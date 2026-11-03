package uz.karvon.admin.api;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.assertj.MockMvcTester;
import org.springframework.test.web.servlet.assertj.MvcTestResult;
import uz.karvon.AbstractIntegrationTest;
import uz.karvon.TestLogins;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static uz.karvon.TestLogins.read;
import static uz.karvon.payment.PaymentTestSupport.as;

class ContentIntegrationTest extends AbstractIntegrationTest {

    @Autowired
    private MockMvcTester mvc;

    private String admin;

    @BeforeEach
    void login() {
        admin = TestLogins.adminToken(mvc);
    }

    @Test
    void bannersAreShownWhenActiveAndInTheirPeriod() {
        Instant now = Instant.now().truncatedTo(ChronoUnit.SECONDS);
        String shown = banner("{\"title\":\"Ko'rinadi\",\"subtitle\":\"Chegirma\",\"imageUrl\":\"https://cdn/1.jpg\","
                + "\"linkUrl\":\"/catalog\",\"sortOrder\":2,\"active\":true,\"activeFrom\":\"%s\",\"activeTo\":\"%s\"}"
                .formatted(now.minusSeconds(3600), now.plusSeconds(3600)));
        String first = banner("{\"title\":\"Birinchi\",\"imageUrl\":\"https://cdn/0.jpg\",\"sortOrder\":1,\"active\":true}");
        String inactive = banner("{\"title\":\"O'chiq\",\"imageUrl\":\"https://cdn/2.jpg\",\"sortOrder\":0,\"active\":false}");
        String later = banner("{\"title\":\"Keyinroq\",\"imageUrl\":\"https://cdn/3.jpg\",\"sortOrder\":0,\"active\":true,"
                + "\"activeFrom\":\"%s\"}".formatted(now.plusSeconds(86400)));

        MvcTestResult visible = mvc.get().uri("/api/v1/banners").exchange();
        assertThat(visible).hasStatusOk();
        List<String> ids = ids(visible);
        assertThat(ids).contains(shown, first).doesNotContain(inactive, later);
        assertThat(ids.indexOf(first)).isLessThan(ids.indexOf(shown));

        assertThat(as(mvc, admin, mvc.get().uri("/api/v1/admin/banners/{id}", shown)))
                .bodyJson().extractingPath("$.subtitle").isEqualTo("Chegirma");
        MvcTestResult wrongPeriod = as(mvc, admin, mvc.put().uri("/api/v1/admin/banners/{id}", shown)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"title\":\"X\",\"imageUrl\":\"https://cdn/1.jpg\",\"sortOrder\":1,\"active\":true,"
                        + "\"activeFrom\":\"%s\",\"activeTo\":\"%s\"}".formatted(now, now)));
        assertThat(wrongPeriod).bodyJson().extractingPath("$.code").isEqualTo("BANNER_PERIOD_INVALID");
        assertThat(as(mvc, admin, mvc.post().uri("/api/v1/admin/banners").contentType(MediaType.APPLICATION_JSON)
                .content("{\"title\":\"\",\"sortOrder\":1}"))).hasStatus(HttpStatus.BAD_REQUEST);

        for (String id : List.of(shown, first, inactive, later)) {
            assertThat(as(mvc, admin, mvc.delete().uri("/api/v1/admin/banners/{id}", id))).hasStatus(HttpStatus.NO_CONTENT);
        }
        assertThat(as(mvc, admin, mvc.get().uri("/api/v1/admin/banners/{id}", shown)))
                .bodyJson().extractingPath("$.code").isEqualTo("BANNER_NOT_FOUND");
    }

    @Test
    void pagesInUzbekAndRussian() {
        MvcTestResult created = as(mvc, admin, mvc.post().uri("/api/v1/admin/pages").contentType(MediaType.APPLICATION_JSON)
                .content(page("yetkazish", true)));
        assertThat(created).hasStatus(HttpStatus.CREATED);
        String id = read(created, "$.id");

        MvcTestResult uz = mvc.get().uri("/api/v1/pages/yetkazish").exchange();
        assertThat(uz).hasStatusOk();
        assertThat(uz).bodyJson().extractingPath("$.lang").isEqualTo("uz");
        assertThat(uz).bodyJson().extractingPath("$.title").isEqualTo("Yetkazib berish");
        assertThat(uz).bodyJson().extractingPath("$.body").isEqualTo("## Muddat\n\n15-25 kun");
        MvcTestResult ru = mvc.get().uri("/api/v1/pages/yetkazish").header(HttpHeaders.ACCEPT_LANGUAGE, "ru").exchange();
        assertThat(ru).bodyJson().extractingPath("$.title").isEqualTo("Доставка");

        assertThat(as(mvc, admin, mvc.post().uri("/api/v1/admin/pages").contentType(MediaType.APPLICATION_JSON)
                .content(page("yetkazish", true)))).bodyJson().extractingPath("$.code").isEqualTo("PAGE_SLUG_TAKEN");
        assertThat(as(mvc, admin, mvc.post().uri("/api/v1/admin/pages").contentType(MediaType.APPLICATION_JSON)
                .content(page("Bad Slug", true)))).hasStatus(HttpStatus.BAD_REQUEST);

        // Unpublished pages are hidden.
        assertThat(as(mvc, admin, mvc.put().uri("/api/v1/admin/pages/{id}", id).contentType(MediaType.APPLICATION_JSON)
                .content(page("yetkazish", false)))).hasStatusOk();
        MvcTestResult hidden = mvc.get().uri("/api/v1/pages/yetkazish").exchange();
        assertThat(hidden).hasStatus(HttpStatus.NOT_FOUND);
        assertThat(hidden).bodyJson().extractingPath("$.code").isEqualTo("PAGE_NOT_FOUND");

        assertThat(as(mvc, admin, mvc.delete().uri("/api/v1/admin/pages/{id}", id))).hasStatus(HttpStatus.NO_CONTENT);
    }

    /** V16 seeds the footer pages; the public offer is a template, so it stays hidden until ADMIN publishes it. */
    @Test
    void footerPagesAreSeededAndTheOfferWaitsForTheCompanyDetails() {
        for (String slug : List.of("faq", "delivery", "returns")) {
            MvcTestResult uz = mvc.get().uri("/api/v1/pages/{slug}", slug).exchange();
            assertThat(uz).as(slug).hasStatusOk();
            assertThat(uz).bodyJson().extractingPath("$.body").asString().as(slug).contains("## ").doesNotContain("[TO'LDIRING");
            MvcTestResult ru = mvc.get().uri("/api/v1/pages/{slug}", slug).header(HttpHeaders.ACCEPT_LANGUAGE, "ru").exchange();
            assertThat(ru).bodyJson().extractingPath("$.lang").isEqualTo("ru");
        }
        assertThat(mvc.get().uri("/api/v1/pages/faq").exchange()).bodyJson().extractingPath("$.title")
                .isEqualTo("Ko'p beriladigan savollar");

        assertThat(mvc.get().uri("/api/v1/pages/public-offer").exchange()).hasStatus(HttpStatus.NOT_FOUND);
        MvcTestResult offers = as(mvc, admin, mvc.get().uri("/api/v1/admin/pages"));
        assertThat(offers).bodyJson().extractingPath("$..[?(@.slug == 'public-offer')].published").asArray().containsExactly(false);
    }

    @Test
    void categoriesCrud() {
        String parent = read(mvc.get().uri("/api/v1/catalog/categories").exchange(), "$[0].slug");
        String parentId = categoryId(parent);

        MvcTestResult created = as(mvc, admin, mvc.post().uri("/api/v1/admin/categories").contentType(MediaType.APPLICATION_JSON)
                .content(category(parentId, "test-sport", "Sport kiyimi", 99)));
        assertThat(created).hasStatus(HttpStatus.CREATED);
        assertThat(created).bodyJson().extractingPath("$.icon").isEqualTo("https://cdn/sport.svg");
        String id = read(created, "$.id");

        MvcTestResult tree = mvc.get().uri("/api/v1/catalog/categories").exchange();
        assertThat(tree).bodyJson().extractingPath("$[0].children[-1:].slug").asArray().containsExactly("test-sport");
        assertThat(tree).bodyJson().extractingPath("$[0].children[-1:].icon").asArray().containsExactly("https://cdn/sport.svg");

        MvcTestResult updated = as(mvc, admin, mvc.put().uri("/api/v1/admin/categories/{id}", id)
                .contentType(MediaType.APPLICATION_JSON).content(category(parentId, "test-sport", "Sport", 99)));
        assertThat(updated).bodyJson().extractingPath("$.nameUz").isEqualTo("Sport");
        assertThat(as(mvc, admin, mvc.post().uri("/api/v1/admin/categories").contentType(MediaType.APPLICATION_JSON)
                .content(category(parentId, "test-sport", "Ikki", 1)))).bodyJson().extractingPath("$.code")
                .isEqualTo("CATEGORY_SLUG_TAKEN");
        // A category cannot be put inside itself or its child.
        assertThat(as(mvc, admin, mvc.put().uri("/api/v1/admin/categories/{id}", parentId)
                .contentType(MediaType.APPLICATION_JSON).content(category(id, parent, "Ichida", 1))))
                .bodyJson().extractingPath("$.code").isEqualTo("CATEGORY_PARENT_INVALID");

        assertThat(as(mvc, admin, mvc.delete().uri("/api/v1/admin/categories/{id}", id))).hasStatus(HttpStatus.NO_CONTENT);
        assertThat(as(mvc, admin, mvc.get().uri("/api/v1/admin/categories/{id}", id))).hasStatus(HttpStatus.NOT_FOUND);
    }

    @Test
    void contentEditingIsForAdminOnly() {
        String operator = TestLogins.staffToken(mvc, "OPERATOR");

        assertThat(as(mvc, operator, mvc.get().uri("/api/v1/admin/banners"))).hasStatus(HttpStatus.FORBIDDEN);
        assertThat(as(mvc, operator, mvc.get().uri("/api/v1/admin/pages"))).hasStatus(HttpStatus.FORBIDDEN);
        assertThat(as(mvc, operator, mvc.get().uri("/api/v1/admin/categories"))).hasStatus(HttpStatus.FORBIDDEN);
        assertThat(as(mvc, operator, mvc.get().uri("/api/v1/admin/promo-codes"))).hasStatus(HttpStatus.FORBIDDEN);
    }

    private String banner(String json) {
        MvcTestResult result = as(mvc, admin, mvc.post().uri("/api/v1/admin/banners").contentType(MediaType.APPLICATION_JSON)
                .content(json));
        assertThat(result).hasStatus(HttpStatus.CREATED);
        return read(result, "$.id");
    }

    private String categoryId(String slug) {
        MvcTestResult all = as(mvc, admin, mvc.get().uri("/api/v1/admin/categories"));
        return read(all, "$[?(@.slug == '%s')].id".formatted(slug)).replaceAll("[\\[\\]\"]", "");
    }

    private static String category(String parentId, String slug, String nameUz, int sortOrder) {
        return "{\"parentId\":%s,\"slug\":\"%s\",\"nameUz\":\"%s\",\"nameRu\":\"Спорт\",\"searchKeywordZh\":\"运动服\","
                .formatted(parentId == null ? "null" : "\"" + parentId + "\"", slug, nameUz)
                + "\"icon\":\"https://cdn/sport.svg\",\"sortOrder\":%d}".formatted(sortOrder);
    }

    private static String page(String slug, boolean published) {
        return ("{\"slug\":\"%s\",\"titleUz\":\"Yetkazib berish\",\"titleRu\":\"Доставка\","
                + "\"bodyUz\":\"## Muddat\\n\\n15-25 kun\",\"bodyRu\":\"## Срок\\n\\n15-25 дней\",\"published\":%s}")
                .formatted(slug, published);
    }

    private static List<String> ids(MvcTestResult result) {
        try {
            return com.jayway.jsonpath.JsonPath.read(result.getResponse().getContentAsString(), "$[*].id");
        } catch (java.io.UnsupportedEncodingException ex) {
            throw new IllegalStateException(ex);
        }
    }
}
