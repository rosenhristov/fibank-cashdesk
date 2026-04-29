package bg.fibank.cashdesk.filter;

import bg.fibank.cashdesk.config.AppProperties;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/**
 * Servlet filter that enforces API-key authentication on every request.
 *
 * <p>The caller must supply the header {@code FIB-X-AUTH} with the value configured
 * under {@code app.auth.api-key} in {@code application.properties}. Requests that
 * omit the header or provide a wrong value receive a {@code 401 Unauthorized} JSON
 * response and are not forwarded to any controller.</p>
 *
 * <p>This filter is registered only for {@code /api/**} patterns by
 * {@link com.example.cashdesk.config.SecurityConfig}, so actuator endpoints
 * (if added later) are not affected.</p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AuthHeaderFilter extends OncePerRequestFilter {

    static final String AUTH_HEADER_NAME = "FIB-X-AUTH";

    private static final String CONTENT_TYPE_JSON = "application/json;charset=UTF-8";

    private final AppProperties appProperties;

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {

        String provided = request.getHeader(AUTH_HEADER_NAME);
        String expected = appProperties.getAuth().getApiKey();

        if (provided == null) {
            log.warn("AUTH | REJECTED | uri={} | reason=missing {} header | remote={}",
                    request.getRequestURI(), AUTH_HEADER_NAME, request.getRemoteAddr());
            writeUnauthorized(response, "Missing required header: " + AUTH_HEADER_NAME);
            return;
        }

        if (!expected.equals(provided)) {
            log.warn("AUTH | REJECTED | uri={} | reason=invalid {} value | remote={}",
                    request.getRequestURI(), AUTH_HEADER_NAME, request.getRemoteAddr());
            writeUnauthorized(response, "Invalid value for header: " + AUTH_HEADER_NAME);
            return;
        }

        log.debug("AUTH | ACCEPTED | uri={} | remote={}",
                request.getRequestURI(), request.getRemoteAddr());
        filterChain.doFilter(request, response);
    }

    // ── helpers ────────────────────────────────────────────────────────────────

    private void writeUnauthorized(HttpServletResponse response, String message) throws IOException {
        response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
        response.setContentType(CONTENT_TYPE_JSON);
        response.getWriter().write(
                """
                {"status":401,"error":"Unauthorized","message":"%s"}
                """.formatted(message).strip()
        );
    }
}