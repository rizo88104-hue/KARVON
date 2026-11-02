package uz.karvon.admin.api;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.assertj.MockMvcTester;
import org.springframework.test.web.servlet.assertj.MvcTestResult;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.mvc.method.RequestMappingInfo;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;
import uz.karvon.AbstractIntegrationTest;
import uz.karvon.TestLogins;
import uz.karvon.common.api.Audited;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static uz.karvon.TestLogins.read;
import static uz.karvon.payment.PaymentTestSupport.as;
import static uz.karvon.payment.PaymentTestSupport.newOrder;
import static uz.karvon.payment.PaymentTestSupport.orderId;
import static uz.karvon.payment.PaymentTestSupport.status;

class AuditIntegrationTest extends AbstractIntegrationTest {

    private static final String AUDIT = "/api/v1/admin/audit";

    @Autowired
    private MockMvcTester mvc;

    @Autowired
    @Qualifier("requestMappingHandlerMapping")
    private RequestMappingHandlerMapping mappings;

    private String admin;

    @BeforeEach
    void login() {
        admin = TestLogins.adminToken(mvc);
    }

    @Test
    void everyAdminWriteEndpointIsAudited() {
        List<String> missing = new ArrayList<>();
        for (Map.Entry<RequestMappingInfo, HandlerMethod> entry : mappings.getHandlerMethods().entrySet()) {
            Set<String> paths = entry.getKey().getPatternValues();
            Set<RequestMethod> methods = entry.getKey().getMethodsCondition().getMethods();
            boolean admin = paths.stream().anyMatch(path -> path.startsWith("/api/v1/admin/"));
            boolean write = methods.isEmpty() || methods.stream().anyMatch(m -> m != RequestMethod.GET && m != RequestMethod.HEAD);
            if (admin && write && !entry.getValue().hasMethodAnnotation(Audited.class)) {
                missing.add(methods + " " + paths);
            }
        }
        assertThat(missing).isEmpty();
    }

    @Test
    void createAndUpdateAreLoggedWithBeforeAndAfter() {
        MvcTestResult created = as(mvc, admin, mvc.post().uri("/api/v1/admin/banners").contentType(MediaType.APPLICATION_JSON)
                .header("X-Forwarded-For", "203.0.113.7, 10.0.0.1")
                .content("{\"title\":\"Audit banner\",\"imageUrl\":\"https://cdn/a.jpg\",\"sortOrder\":1,\"active\":true}"));
        assertThat(created).hasStatus(HttpStatus.CREATED);
        String id = read(created, "$.id");
        assertThat(as(mvc, admin, mvc.put().uri("/api/v1/admin/banners/{id}", id).contentType(MediaType.APPLICATION_JSON)
                .content("{\"title\":\"Audit banner 2\",\"imageUrl\":\"https://cdn/a.jpg\",\"sortOrder\":1,\"active\":false}")))
                .hasStatusOk();
        assertThat(as(mvc, admin, mvc.delete().uri("/api/v1/admin/banners/{id}", id))).hasStatus(HttpStatus.NO_CONTENT);

        MvcTestResult log = as(mvc, admin, mvc.get().uri(AUDIT + "?entityType=BANNER&entityId={id}", id));
        assertThat(log).hasStatusOk();
        assertThat(log).bodyJson().extractingPath("$.totalElements").isEqualTo(3);
        // newest first
        assertThat(log).bodyJson().extractingPath("$.content[0].action").isEqualTo("BANNER_DELETE");
        assertThat(log).bodyJson().extractingPath("$.content[0].before").asString().contains("Audit banner 2");
        assertThat(log).bodyJson().extractingPath("$.content[0].after").isNull();
        assertThat(log).bodyJson().extractingPath("$.content[1].action").isEqualTo("BANNER_UPDATE");
        assertThat(log).bodyJson().extractingPath("$.content[1].before").asString().contains("\"active\":true");
        assertThat(log).bodyJson().extractingPath("$.content[1].after").asString().contains("\"active\":false");
        assertThat(log).bodyJson().extractingPath("$.content[2].action").isEqualTo("BANNER_CREATE");
        assertThat(log).bodyJson().extractingPath("$.content[2].before").isNull();
        assertThat(log).bodyJson().extractingPath("$.content[2].after").asString().contains("Audit banner");
        assertThat(log).bodyJson().extractingPath("$.content[2].actorRole").isEqualTo("ADMIN");
        assertThat(log).bodyJson().extractingPath("$.content[2].ip").isEqualTo("203.0.113.7");
        assertThat(log).bodyJson().extractingPath("$.content[2].actorId").isEqualTo(read(
                as(mvc, admin, mvc.get().uri("/api/v1/me")), "$.id"));
    }

    @Test
    void orderStatusChangesAreLoggedForTheStaffRole() {
        String customer = TestLogins.accessToken(mvc, TestLogins.newPhone());
        String operator = TestLogins.staffToken(mvc, "OPERATOR");
        String id = orderId(mvc, admin, newOrder(mvc, customer));
        assertThat(status(mvc, admin, id, "{\"toStatus\":\"PAID\"}")).hasStatusOk();
        assertThat(as(mvc, operator, mvc.post().uri("/api/v1/admin/orders/{id}/comments", id)
                .contentType(MediaType.APPLICATION_JSON).content("{\"text\":\"Mijoz qo'ng'iroq qildi\"}")))
                .hasStatus(HttpStatus.CREATED);
        // A refused call changes nothing and is not logged.
        assertThat(status(mvc, admin, id, "{\"toStatus\":\"DELIVERED\"}")).hasStatus(HttpStatus.BAD_REQUEST);

        MvcTestResult log = as(mvc, admin, mvc.get().uri(AUDIT + "?entityType=ORDER&entityId={id}", id));
        assertThat(log).bodyJson().extractingPath("$.totalElements").isEqualTo(2);
        assertThat(log).bodyJson().extractingPath("$.content[0].action").isEqualTo("ORDER_COMMENT_ADD");
        assertThat(log).bodyJson().extractingPath("$.content[0].actorRole").isEqualTo("OPERATOR");
        assertThat(log).bodyJson().extractingPath("$.content[1].action").isEqualTo("ORDER_STATUS_CHANGE");
        assertThat(log).bodyJson().extractingPath("$.content[1].before").asString().contains("\"status\":\"AWAITING_PAYMENT\"");
        assertThat(log).bodyJson().extractingPath("$.content[1].after").asString().contains("\"status\":\"PAID\"");

        MvcTestResult byAction = as(mvc, admin, mvc.get().uri(AUDIT + "?action=ORDER_COMMENT_ADD&entityId={id}", id));
        assertThat(byAction).bodyJson().extractingPath("$.totalElements").isEqualTo(1);
    }

    @Test
    void onlyAdminReadsTheLog() {
        String operator = TestLogins.staffToken(mvc, "OPERATOR");

        assertThat(as(mvc, operator, mvc.get().uri(AUDIT))).hasStatus(HttpStatus.FORBIDDEN);
        assertThat(as(mvc, admin, mvc.get().uri(AUDIT + "?size=500"))).hasStatus(HttpStatus.BAD_REQUEST);
    }
}
