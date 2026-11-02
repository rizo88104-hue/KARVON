package uz.karvon.order.api;

import jakarta.persistence.EntityManagerFactory;
import org.hibernate.SessionFactory;
import org.hibernate.stat.Statistics;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.assertj.MockMvcTester;
import uz.karvon.AbstractIntegrationTest;
import uz.karvon.TestLogins;
import uz.karvon.payment.PaymentTestSupport;

import static org.assertj.core.api.Assertions.assertThat;
import static uz.karvon.TestLogins.bearer;

/**
 * List pages must not run one query per row (N+1): the number of SQL statements of a page does not depend on how
 * many orders or users are on it (order items and user roles are batch-loaded, customer phones fetched at once).
 */
@TestPropertySource(properties = {
        "spring.jpa.properties.hibernate.generate_statistics=true",
        "logging.level.org.hibernate.engine.internal.StatisticalLoggingSessionEventListener=WARN",
})
class ListQueryCountIntegrationTest extends AbstractIntegrationTest {

    @Autowired
    private MockMvcTester mvc;

    @Autowired
    private EntityManagerFactory entityManagerFactory;

    private Statistics statistics;
    private String admin;

    @BeforeEach
    void setUp() {
        statistics = entityManagerFactory.unwrap(SessionFactory.class).getStatistics();
        admin = TestLogins.adminToken(mvc);
    }

    @Test
    void staffOrderListAndCustomerOrderListDoNotGrowWithTheRows() {
        String phone = TestLogins.newPhone();
        String customer = TestLogins.accessToken(mvc, phone);
        String digits = phone.substring(1);
        PaymentTestSupport.newOrder(mvc, customer);
        long staffOne = statements("/api/v1/admin/orders?phone=" + digits, admin);
        long mineOne = statements("/api/v1/orders", customer);

        // Five more orders, each by a different customer for the unfiltered staff list.
        for (int i = 0; i < 5; i++) {
            PaymentTestSupport.newOrder(mvc, customer);
            PaymentTestSupport.newOrder(mvc, TestLogins.accessToken(mvc, TestLogins.newPhone()));
        }
        long staffSix = statements("/api/v1/admin/orders?phone=" + digits, admin);
        long mineSix = statements("/api/v1/orders", customer);
        long staffAll = statements("/api/v1/admin/orders?size=20", admin);

        assertThat(staffSix).as("staff search: 1 vs 6 orders").isEqualTo(staffOne);
        assertThat(mineSix).as("customer list: 1 vs 6 orders").isEqualTo(mineOne);
        assertThat(staffAll).as("20 orders of many customers").isLessThanOrEqualTo(staffOne + 1);
    }

    @Test
    void adminUserListLoadsRolesInOneBatch() {
        long few = statements("/api/v1/admin/users?size=1", admin);
        for (int i = 0; i < 5; i++) {
            TestLogins.login(mvc, TestLogins.newPhone());
        }
        long many = statements("/api/v1/admin/users?size=20", admin);

        assertThat(many).as("roles of 20 users vs 1 user").isLessThanOrEqualTo(few + 1);
    }

    /** SQL statements Hibernate prepared while serving one GET. */
    private long statements(String uri, String token) {
        statistics.clear();
        assertThat(mvc.get().uri(uri).header(HttpHeaders.AUTHORIZATION, bearer(token)).exchange()).hasStatusOk();
        return statistics.getPrepareStatementCount();
    }
}
