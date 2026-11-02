package uz.karvon.common.infrastructure.web;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/**
 * Refuses non-multipart bodies larger than karvon.security.max-json-body (413 REQUEST_TOO_LARGE) before they are
 * read. Multipart uploads have their own limits (spring.servlet.multipart.*); nginx also caps the body size.
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 10)
@RequiredArgsConstructor
public class RequestSizeFilter extends OncePerRequestFilter {

    private final WebSecurityProperties properties;
    private final ProblemResponses problems;

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        String contentType = request.getContentType();
        boolean multipart = contentType != null && contentType.toLowerCase().startsWith("multipart/");
        long limit = properties.maxJsonBody().toBytes();
        if (!multipart && request.getContentLengthLong() > limit) {
            problems.write(request, response, HttpStatus.CONTENT_TOO_LARGE, "REQUEST_TOO_LARGE",
                    "error.request.tooLarge", limit / 1024);
            return;
        }
        chain.doFilter(request, response);
    }
}
