package uz.karvon.common.api;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.assertj.MockMvcTester;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;
import uz.karvon.auth.infrastructure.JwtUserAuthenticator;
import uz.karvon.auth.infrastructure.SecurityConfig;
import uz.karvon.common.application.BusinessException;
import uz.karvon.common.application.NotFoundException;
import uz.karvon.common.infrastructure.I18nConfig;

import static org.assertj.core.api.Assertions.assertThat;

@WebMvcTest(controllers = GlobalExceptionHandlerTest.FailingController.class)
@Import({GlobalExceptionHandlerTest.FailingController.class, SecurityConfig.class, I18nConfig.class})
@WithMockUser
class GlobalExceptionHandlerTest {

    @MockitoBean
    private JwtDecoder jwtDecoder;

    @MockitoBean
    private JwtUserAuthenticator jwtUserAuthenticator;

    @Autowired
    private MockMvcTester mvc;

    @Test
    void businessExceptionBecomesProblemDetailWithCodeAndUzbekText() {
        var result = mvc.get().uri("/test/business");

        assertThat(result).hasStatus(HttpStatus.BAD_REQUEST)
                .hasContentType(MediaType.APPLICATION_PROBLEM_JSON);
        assertThat(result).bodyJson().extractingPath("$.code").isEqualTo("TEST_RULE_BROKEN");
        assertThat(result).bodyJson().extractingPath("$.detail").isEqualTo("So'rov noto'g'ri");
        assertThat(result).bodyJson().extractingPath("$.status").isEqualTo(400);
    }

    @Test
    void textIsRussianWhenAskedFor() {
        var result = mvc.get().uri("/test/business").header(HttpHeaders.ACCEPT_LANGUAGE, "ru");

        assertThat(result).bodyJson().extractingPath("$.detail").isEqualTo("Некорректный запрос");
    }

    @Test
    void unsupportedLanguageFallsBackToUzbek() {
        var result = mvc.get().uri("/test/business").header(HttpHeaders.ACCEPT_LANGUAGE, "en");

        assertThat(result).bodyJson().extractingPath("$.detail").isEqualTo("So'rov noto'g'ri");
    }

    @Test
    void notFoundExceptionReturns404() {
        var result = mvc.get().uri("/test/not-found");

        assertThat(result).hasStatus(HttpStatus.NOT_FOUND);
        assertThat(result).bodyJson().extractingPath("$.code").isEqualTo("TEST_NOT_FOUND");
        assertThat(result).bodyJson().extractingPath("$.detail").isEqualTo("So'ralgan manzil topilmadi");
    }

    @Test
    void validationErrorListsFields() {
        var result = mvc.post().uri("/test/validated")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\":\"\"}");

        assertThat(result).hasStatus(HttpStatus.BAD_REQUEST);
        assertThat(result).bodyJson().extractingPath("$.code").isEqualTo("VALIDATION_ERROR");
        assertThat(result).bodyJson().extractingPath("$.errors.name").isNotNull();
    }

    @Test
    void springMvcErrorsAlsoGetCode() {
        var result = mvc.delete().uri("/test/business");

        assertThat(result).hasStatus(HttpStatus.METHOD_NOT_ALLOWED);
        assertThat(result).bodyJson().extractingPath("$.code").isEqualTo("METHOD_NOT_ALLOWED");
    }

    @Test
    void unexpectedExceptionHidesDetails() {
        var result = mvc.get().uri("/test/crash");

        assertThat(result).hasStatus(HttpStatus.INTERNAL_SERVER_ERROR);
        assertThat(result).bodyJson().extractingPath("$.code").isEqualTo("INTERNAL_ERROR");
        assertThat(result).bodyText().doesNotContain("secret");
    }

    @RestController
    static class FailingController {

        @GetMapping("/test/business")
        void business() {
            throw new BusinessException("TEST_RULE_BROKEN", "error.http.400");
        }

        @GetMapping("/test/not-found")
        void notFound() {
            throw new NotFoundException("TEST_NOT_FOUND", "error.http.404");
        }

        @GetMapping("/test/crash")
        void crash() {
            throw new IllegalStateException("secret internal detail");
        }

        @PostMapping("/test/validated")
        void validated(@Valid @RequestBody NamedRequest request) {
        }
    }

    record NamedRequest(@NotBlank String name) {
    }
}
