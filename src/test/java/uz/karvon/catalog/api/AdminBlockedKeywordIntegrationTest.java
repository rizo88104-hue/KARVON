package uz.karvon.catalog.api;

import com.jayway.jsonpath.JsonPath;
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

import java.io.UnsupportedEncodingException;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static uz.karvon.TestLogins.accessToken;
import static uz.karvon.TestLogins.bearer;
import static uz.karvon.TestLogins.newPhone;
import static uz.karvon.TestLogins.read;

class AdminBlockedKeywordIntegrationTest extends AbstractIntegrationTest {

    private static final String URL = "/api/v1/admin/blocked-keywords";

    @Autowired
    private MockMvcTester mvc;

    private String adminToken;

    @BeforeEach
    void loginAsAdmin() {
        adminToken = TestLogins.adminToken(mvc);
    }

    @Test
    void seedHasKeywordsForEveryCategoryAndLanguage() {
        for (String category : new String[]{"LIQUID", "BATTERY", "WEAPON", "MEDICINE", "REPLICA"}) {
            for (String lang : new String[]{"UZ", "RU", "ZH"}) {
                var page = admin(mvc.get().uri(URL + "?category={c}&lang={l}", category, lang));
                assertThat(page).as(category + "/" + lang).bodyJson().extractingPath("$.totalElements")
                        .asNumber().satisfies(n -> assertThat(n.intValue()).isPositive());
            }
        }
    }

    @Test
    void newKeywordHidesProductsAtOnceAndDeletingShowsThemAgain() {
        // 无线鼠标静音 (wireless mouse) is a normal fake product.
        assertThat(searchTitles("sichqoncha")).contains("无线鼠标静音");

        MvcTestResult created = admin(mvc.post().uri(URL).contentType(MediaType.APPLICATION_JSON)
                .content("{\"keyword\":\" 鼠标 \",\"lang\":\"ZH\",\"category\":\"OTHER\"}"));
        assertThat(created).hasStatus(HttpStatus.CREATED);
        assertThat(created).bodyJson().extractingPath("$.keyword").isEqualTo("鼠标");
        String id = read(created, "$.id");

        assertThat(searchTitles("sichqoncha")).doesNotContain("无线鼠标静音");

        assertThat(admin(mvc.delete().uri(URL + "/{id}", id))).hasStatus(HttpStatus.NO_CONTENT);
        assertThat(searchTitles("sichqoncha")).contains("无线鼠标静音");
    }

    @Test
    void updateAndDuplicates() {
        MvcTestResult created = admin(mvc.post().uri(URL).contentType(MediaType.APPLICATION_JSON)
                .content("{\"keyword\":\"Portlovchi\",\"lang\":\"UZ\",\"category\":\"OTHER\"}"));
        String id = read(created, "$.id");

        MvcTestResult duplicate = admin(mvc.post().uri(URL).contentType(MediaType.APPLICATION_JSON)
                .content("{\"keyword\":\"portlovchi\",\"lang\":\"UZ\",\"category\":\"WEAPON\"}"));
        assertThat(duplicate).hasStatus(HttpStatus.CONFLICT);
        assertThat(duplicate).bodyJson().extractingPath("$.code").isEqualTo("BLOCKED_KEYWORD_EXISTS");

        MvcTestResult updated = admin(mvc.put().uri(URL + "/{id}", id).contentType(MediaType.APPLICATION_JSON)
                .content("{\"keyword\":\"portlovchi modda\",\"lang\":\"UZ\",\"category\":\"WEAPON\"}"));
        assertThat(updated).hasStatusOk();
        assertThat(updated).bodyJson().extractingPath("$.category").isEqualTo("WEAPON");

        assertThat(admin(mvc.get().uri(URL + "?q=portlovchi"))).bodyJson().extractingPath("$.totalElements").isEqualTo(1);
        assertThat(admin(mvc.delete().uri(URL + "/{id}", id))).hasStatus(HttpStatus.NO_CONTENT);
        assertThat(admin(mvc.delete().uri(URL + "/{id}", id))).hasStatus(HttpStatus.NOT_FOUND);
    }

    @Test
    void customerCannotManageKeywords() {
        String customer = accessToken(mvc, newPhone());

        assertThat(mvc.get().uri(URL).header(HttpHeaders.AUTHORIZATION, bearer(customer))).hasStatus(HttpStatus.FORBIDDEN);
        assertThat(mvc.get().uri(URL)).hasStatus(HttpStatus.UNAUTHORIZED);
    }

    private List<Object> searchTitles(String query) {
        MvcTestResult result = mvc.get().uri("/api/v1/catalog/search?q={q}", query).exchange();
        assertThat(result).hasStatusOk();
        return JsonPath.read(responseText(result), "$.content[*].titleOriginal");
    }

    private static String responseText(MvcTestResult result) {
        try {
            return result.getResponse().getContentAsString();
        } catch (UnsupportedEncodingException ex) {
            throw new IllegalStateException(ex);
        }
    }

    private MvcTestResult admin(MockMvcTester.MockMvcRequestBuilder request) {
        return request.header(HttpHeaders.AUTHORIZATION, bearer(adminToken)).exchange();
    }
}
