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
import uz.karvon.TestLogins;

import static org.assertj.core.api.Assertions.assertThat;
import static uz.karvon.TestLogins.ADMIN_PHONE;
import static uz.karvon.TestLogins.accessToken;
import static uz.karvon.TestLogins.bearer;
import static uz.karvon.TestLogins.login;
import static uz.karvon.TestLogins.newPhone;
import static uz.karvon.TestLogins.read;
import static uz.karvon.TestLogins.refresh;
import static uz.karvon.TestLogins.requestOtp;

/**
 * The ADMIN is created on startup from ADMIN_PHONE (+998900000001 in application-test.yml).
 */
class AdminUserIntegrationTest extends AbstractIntegrationTest {

    @Autowired
    private MockMvcTester mvc;

    private String adminToken;

    @BeforeEach
    void loginAsAdmin() {
        adminToken = TestLogins.adminToken(mvc);
    }

    @Test
    void adminFromEnvironmentExistsWithAdminRole() {
        var me = mvc.get().uri("/api/v1/me").header(HttpHeaders.AUTHORIZATION, bearer(adminToken));

        assertThat(me).bodyJson().extractingPath("$.phone").isEqualTo(ADMIN_PHONE);
        assertThat(me).bodyJson().extractingPath("$.roles").asArray().containsExactly("ADMIN");
    }

    @Test
    void customerGets403OnAdminEndpoints() {
        String customerToken = accessToken(mvc, newPhone());

        var list = mvc.get().uri("/api/v1/admin/users").header(HttpHeaders.AUTHORIZATION, bearer(customerToken));
        assertThat(list).hasStatus(HttpStatus.FORBIDDEN);
        assertThat(list).bodyJson().extractingPath("$.code").isEqualTo("ACCESS_DENIED");

        // Any /api/v1/admin/** path, even one without a controller.
        assertThat(mvc.get().uri("/api/v1/admin/orders").header(HttpHeaders.AUTHORIZATION, bearer(customerToken)))
                .hasStatus(HttpStatus.FORBIDDEN);
    }

    @Test
    void staffWithoutAdminRoleGets403OnUserManagement() {
        String operatorPhone = newPhone();
        String operatorId = read(login(mvc, operatorPhone), "$.user.id");
        assertThat(patch("/api/v1/admin/users/" + operatorId + "/roles", "{\"roles\":[\"OPERATOR\"]}"))
                .hasStatusOk();
        String operatorToken = accessToken(mvc, operatorPhone);

        assertThat(mvc.get().uri("/api/v1/admin/users").header(HttpHeaders.AUTHORIZATION, bearer(operatorToken)))
                .hasStatus(HttpStatus.FORBIDDEN);
    }

    @Test
    void adminListsUsersWithFilters() {
        String phone = newPhone();
        login(mvc, phone);

        var byPhone = mvc.get().uri("/api/v1/admin/users?phone={phone}&role=CUSTOMER&status=ACTIVE", phone.substring(4))
                .header(HttpHeaders.AUTHORIZATION, bearer(adminToken));

        assertThat(byPhone).hasStatusOk();
        assertThat(byPhone).bodyJson().extractingPath("$.totalElements").isEqualTo(1);
        assertThat(byPhone).bodyJson().extractingPath("$.content[0].phone").isEqualTo(phone);

        var admins = mvc.get().uri("/api/v1/admin/users?role=ADMIN&size=5")
                .header(HttpHeaders.AUTHORIZATION, bearer(adminToken));
        assertThat(admins).bodyJson().extractingPath("$.size").isEqualTo(5);
        assertThat(admins).bodyJson().extractingPath("$.content[*].phone").asArray().contains(ADMIN_PHONE);
    }

    @Test
    void unknownSortFieldIs400() {
        var result = mvc.get().uri("/api/v1/admin/users?sort=nope").header(HttpHeaders.AUTHORIZATION, bearer(adminToken));

        assertThat(result).hasStatus(HttpStatus.BAD_REQUEST);
        assertThat(result).bodyJson().extractingPath("$.code").isEqualTo("INVALID_SORT");
    }

    @Test
    void adminChangesRolesAndTheyApplyToExistingTokens() {
        String phone = newPhone();
        MvcTestResult login = login(mvc, phone);
        String userId = read(login, "$.user.id");
        String userToken = read(login, "$.accessToken");

        MvcTestResult result = patch("/api/v1/admin/users/" + userId + "/roles", "{\"roles\":[\"CUSTOMER\",\"BUYER\"]}");

        assertThat(result).hasStatusOk();
        assertThat(result).bodyJson().extractingPath("$.roles").asArray().containsExactlyInAnyOrder("CUSTOMER", "BUYER");
        // The token from before the change already passes the staff URL rule (404: no such page, not 403).
        var staffArea = mvc.get().uri("/api/v1/admin/not-there-yet").header(HttpHeaders.AUTHORIZATION, bearer(userToken));
        assertThat(staffArea).hasStatus(HttpStatus.NOT_FOUND);
    }

    @Test
    void emptyRolesAreRejected() {
        String userId = read(login(mvc, newPhone()), "$.user.id");

        MvcTestResult result = patch("/api/v1/admin/users/" + userId + "/roles", "{\"roles\":[]}");

        assertThat(result).hasStatus(HttpStatus.BAD_REQUEST);
        assertThat(result).bodyJson().extractingPath("$.code").isEqualTo("VALIDATION_ERROR");
    }

    @Test
    void blockedUserCannotUseTokensRefreshOrLogIn() {
        String phone = newPhone();
        MvcTestResult login = login(mvc, phone);
        String userId = read(login, "$.user.id");

        MvcTestResult blocked = patch("/api/v1/admin/users/" + userId + "/status", "{\"status\":\"BLOCKED\"}");
        assertThat(blocked).hasStatusOk();
        assertThat(blocked).bodyJson().extractingPath("$.status").isEqualTo("BLOCKED");

        var me = mvc.get().uri("/api/v1/me").header(HttpHeaders.AUTHORIZATION, bearer(read(login, "$.accessToken")));
        assertThat(me).hasStatus(HttpStatus.FORBIDDEN);
        assertThat(me).bodyJson().extractingPath("$.code").isEqualTo("USER_BLOCKED");

        assertThat(refresh(mvc, read(login, "$.refreshToken"))).hasStatus(HttpStatus.UNAUTHORIZED);

        MvcTestResult otp = requestOtp(mvc, phone);
        assertThat(otp).hasStatus(HttpStatus.FORBIDDEN);
        assertThat(otp).bodyJson().extractingPath("$.code").isEqualTo("USER_BLOCKED");

        // Unblocking lets the user log in again.
        assertThat(patch("/api/v1/admin/users/" + userId + "/status", "{\"status\":\"ACTIVE\"}")).hasStatusOk();
        login(mvc, phone);
    }

    @Test
    void adminCannotBlockThemselvesOrDropOwnAdminRole() {
        String adminId = read(mvc.get().uri("/api/v1/me").header(HttpHeaders.AUTHORIZATION, bearer(adminToken)).exchange(), "$.id");

        MvcTestResult block = patch("/api/v1/admin/users/" + adminId + "/status", "{\"status\":\"BLOCKED\"}");
        assertThat(block).hasStatus(HttpStatus.BAD_REQUEST);
        assertThat(block).bodyJson().extractingPath("$.code").isEqualTo("CANNOT_CHANGE_OWN_ACCOUNT");

        assertThat(patch("/api/v1/admin/users/" + adminId + "/roles", "{\"roles\":[\"OPERATOR\"]}"))
                .hasStatus(HttpStatus.BAD_REQUEST);
    }

    @Test
    void unknownUserIs404() {
        MvcTestResult result = patch("/api/v1/admin/users/00000000-0000-0000-0000-000000000000/status",
                "{\"status\":\"BLOCKED\"}");

        assertThat(result).hasStatus(HttpStatus.NOT_FOUND);
        assertThat(result).bodyJson().extractingPath("$.code").isEqualTo("USER_NOT_FOUND");
    }

    private MvcTestResult patch(String uri, String body) {
        return mvc.patch().uri(uri)
                .header(HttpHeaders.AUTHORIZATION, bearer(adminToken))
                .contentType(MediaType.APPLICATION_JSON)
                .content(body)
                .exchange();
    }
}
