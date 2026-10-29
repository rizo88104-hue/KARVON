package uz.karvon.user.api;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.assertj.MockMvcTester;
import org.springframework.test.web.servlet.assertj.MvcTestResult;
import uz.karvon.AbstractIntegrationTest;

import static org.assertj.core.api.Assertions.assertThat;
import static uz.karvon.TestLogins.accessToken;
import static uz.karvon.TestLogins.bearer;
import static uz.karvon.TestLogins.newPhone;
import static uz.karvon.TestLogins.read;

class MeIntegrationTest extends AbstractIntegrationTest {

    private static final String ADDRESS = """
            {"label":"Uy","region":"Toshkent","city":"Toshkent","district":"Chilonzor",
             "street":"Bunyodkor 1","house":"12","apartment":"34","landmark":"Metro yonida",
             "phone":"%s","isDefault":%s}""";

    @Autowired
    private MockMvcTester mvc;

    private String token;

    @BeforeEach
    void login() {
        token = accessToken(mvc, newPhone());
    }

    @Test
    void updatesProfile() {
        MvcTestResult updated = send(mvc.patch().uri("/api/v1/me"), "{\"fullName\":\"  Ali Valiyev \",\"language\":\"RU\"}");

        assertThat(updated).hasStatusOk();
        assertThat(updated).bodyJson().extractingPath("$.fullName").isEqualTo("Ali Valiyev");
        assertThat(updated).bodyJson().extractingPath("$.language").isEqualTo("RU");

        // Fields left out stay as they were.
        MvcTestResult partial = send(mvc.patch().uri("/api/v1/me"), "{\"language\":\"UZ\"}");
        assertThat(partial).bodyJson().extractingPath("$.fullName").isEqualTo("Ali Valiyev");
        assertThat(mvc.get().uri("/api/v1/me").header(HttpHeaders.AUTHORIZATION, bearer(token)))
                .bodyJson().extractingPath("$.language").isEqualTo("UZ");
    }

    @Test
    void addressCrudKeepsExactlyOneDefault() {
        MvcTestResult first = createAddress("", false);
        assertThat(first).hasStatus(HttpStatus.CREATED);
        assertThat(first).bodyJson().extractingPath("$.isDefault").isEqualTo(true);
        assertThat(first).bodyJson().extractingPath("$.phone").isNull();
        String firstId = read(first, "$.id");

        MvcTestResult second = createAddress("+998 90 111-22-33", true);
        assertThat(second).bodyJson().extractingPath("$.isDefault").isEqualTo(true);
        assertThat(second).bodyJson().extractingPath("$.phone").isEqualTo("+998901112233");
        String secondId = read(second, "$.id");

        var list = mvc.get().uri("/api/v1/me/addresses").header(HttpHeaders.AUTHORIZATION, bearer(token));
        assertThat(list).bodyJson().extractingPath("$[?(@.isDefault == true)].id").asArray().containsExactly(secondId);

        MvcTestResult update = send(mvc.put().uri("/api/v1/me/addresses/{id}", firstId), ADDRESS.formatted("", true));
        assertThat(update).hasStatusOk();
        assertThat(update).bodyJson().extractingPath("$.isDefault").isEqualTo(true);
        assertThat(get(secondId)).bodyJson().extractingPath("$.isDefault").isEqualTo(false);

        // Deleting the default makes the remaining address the default.
        assertThat(mvc.delete().uri("/api/v1/me/addresses/{id}", firstId).header(HttpHeaders.AUTHORIZATION, bearer(token)))
                .hasStatus(HttpStatus.NO_CONTENT);
        assertThat(get(firstId)).hasStatus(HttpStatus.NOT_FOUND);
        assertThat(get(secondId)).bodyJson().extractingPath("$.isDefault").isEqualTo(true);
    }

    @Test
    void atMostTenAddresses() {
        for (int i = 0; i < 10; i++) {
            assertThat(createAddress("", false)).hasStatus(HttpStatus.CREATED);
        }

        MvcTestResult eleventh = createAddress("", false);

        assertThat(eleventh).hasStatus(HttpStatus.BAD_REQUEST);
        assertThat(eleventh).bodyJson().extractingPath("$.code").isEqualTo("ADDRESS_LIMIT_REACHED");
        assertThat(eleventh).bodyJson().extractingPath("$.detail").isEqualTo("Ko'pi bilan 10 ta manzil saqlash mumkin");
    }

    @Test
    void otherUsersAddressIsNotFound() {
        String addressId = read(createAddress("", false), "$.id");
        token = accessToken(mvc, newPhone());

        MvcTestResult result = get(addressId);

        assertThat(result).hasStatus(HttpStatus.NOT_FOUND);
        assertThat(result).bodyJson().extractingPath("$.code").isEqualTo("ADDRESS_NOT_FOUND");
    }

    @Test
    void addressValidation() {
        MvcTestResult missing = send(mvc.post().uri("/api/v1/me/addresses"), "{\"region\":\"Toshkent\"}");
        assertThat(missing).hasStatus(HttpStatus.BAD_REQUEST);
        assertThat(missing).bodyJson().extractingPath("$.errors.city").isNotNull();

        MvcTestResult badPhone = createAddress("+7 900 123", false);
        assertThat(badPhone).hasStatus(HttpStatus.BAD_REQUEST);
        assertThat(badPhone).bodyJson().extractingPath("$.code").isEqualTo("PHONE_INVALID");
    }

    private MvcTestResult createAddress(String phone, boolean isDefault) {
        return send(mvc.post().uri("/api/v1/me/addresses"), ADDRESS.formatted(phone, isDefault));
    }

    private MvcTestResult get(String addressId) {
        return mvc.get().uri("/api/v1/me/addresses/{id}", addressId).header(HttpHeaders.AUTHORIZATION, bearer(token)).exchange();
    }

    private MvcTestResult send(MockMvcTester.MockMvcRequestBuilder request, String body) {
        return request.header(HttpHeaders.AUTHORIZATION, bearer(token))
                .contentType(MediaType.APPLICATION_JSON)
                .content(body)
                .exchange();
    }
}
