package uz.karvon.common.api;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.assertj.MockMvcTester;
import uz.karvon.AbstractIntegrationTest;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

class PingIntegrationTest extends AbstractIntegrationTest {

    @Autowired
    private MockMvcTester mvc;

    @Autowired
    private JdbcTemplate jdbc;

    @Test
    void pingIsPublicAndReturnsStatusAndTime() {
        Instant before = Instant.now();

        var result = mvc.get().uri("/api/v1/ping");

        assertThat(result).hasStatusOk();
        assertThat(result).bodyJson().extractingPath("$.status").isEqualTo("ok");
        assertThat(result).bodyJson().extractingPath("$.time").asString()
                .satisfies(time -> assertThat(Instant.parse(time)).isAfterOrEqualTo(before.minusSeconds(1)));
    }

    @Test
    void healthIsPublicAndUp() {
        var result = mvc.get().uri("/actuator/health");

        assertThat(result).hasStatusOk();
        assertThat(result).bodyJson().extractingPath("$.status").isEqualTo("UP");
    }

    @Test
    void otherEndpointsRequireAuthentication() {
        assertThat(mvc.get().uri("/api/v1/anything")).hasStatus(HttpStatus.UNAUTHORIZED);
    }

    @Test
    void flywayMigrationsRan() {
        String schema = jdbc.queryForObject("SELECT value FROM app_info WHERE name = 'schema'", String.class);

        assertThat(schema).isEqualTo("v1");
    }
}
