package uz.karvon.admin.api;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.test.web.servlet.assertj.MockMvcTester;
import org.springframework.test.web.servlet.assertj.MvcTestResult;
import uz.karvon.AbstractIntegrationTest;
import uz.karvon.TestLogins;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static uz.karvon.TestLogins.read;
import static uz.karvon.payment.PaymentTestSupport.as;
import static uz.karvon.payment.PaymentTestSupport.newOrder;
import static uz.karvon.payment.PaymentTestSupport.orderId;
import static uz.karvon.payment.PaymentTestSupport.status;

class AdminCustomerIntegrationTest extends AbstractIntegrationTest {

    @Autowired
    private MockMvcTester mvc;

    @Test
    void operatorSeesProfileOrdersWalletAndNotifications() {
        String admin = TestLogins.adminToken(mvc);
        String phone = TestLogins.newPhone();
        MvcTestResult login = TestLogins.login(mvc, phone);
        String customer = read(login, "$.accessToken");
        String customerId = read(login, "$.user.id");
        String number = newOrder(mvc, customer);
        // Paying queues a notification for the customer.
        assertThat(status(mvc, admin, orderId(mvc, admin, number), "{\"toStatus\":\"PAID\"}")).hasStatusOk();
        String operator = TestLogins.staffToken(mvc, "OPERATOR");

        MvcTestResult result = as(mvc, operator, mvc.get().uri("/api/v1/admin/customers/{id}", customerId));

        assertThat(result).hasStatusOk();
        assertThat(result).bodyJson().extractingPath("$.profile.phone").isEqualTo(phone);
        assertThat(result).bodyJson().extractingPath("$.orders.totalElements").isEqualTo(1);
        assertThat(result).bodyJson().extractingPath("$.orders.content[0].number").isEqualTo(number);
        assertThat(result).bodyJson().extractingPath("$.orders.content[0].status").isEqualTo("PAID");
        assertThat(result).bodyJson().extractingPath("$.wallet.balanceUzs").isEqualTo(0);
        assertThat(result).bodyJson().extractingPath("$.notifications.totalElements").isNotEqualTo(0);
        assertThat(result).bodyJson().extractingPath("$.notifications.content[0].userId").isEqualTo(customerId);
    }

    @Test
    void unknownCustomerAndRoles() {
        String operator = TestLogins.staffToken(mvc, "OPERATOR");
        String buyer = TestLogins.staffToken(mvc, "BUYER");

        MvcTestResult unknown = as(mvc, operator, mvc.get().uri("/api/v1/admin/customers/{id}", UUID.randomUUID()));
        assertThat(unknown).hasStatus(HttpStatus.NOT_FOUND);
        assertThat(unknown).bodyJson().extractingPath("$.code").isEqualTo("USER_NOT_FOUND");
        assertThat(as(mvc, buyer, mvc.get().uri("/api/v1/admin/customers/{id}", UUID.randomUUID())))
                .hasStatus(HttpStatus.FORBIDDEN);
    }
}
