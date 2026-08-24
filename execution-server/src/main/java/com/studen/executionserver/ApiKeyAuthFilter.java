package com.studen.executionserver;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * The only auth this app has: every {@code /compile}/{@code /run} request must carry
 * {@code X-Execution-Api-Key} matching {@code app.api-key} exactly, or it's rejected with 401
 * before touching Docker at all. {@code /health} stays open (same posture as the main backend's
 * own public {@code /actuator/health} -- reveals nothing beyond up/down). Fails closed: a blank
 * configured key (unset {@code EXECUTION_API_KEY}) means every authenticated request is rejected,
 * never silently accepted.
 */
@Component
public class ApiKeyAuthFilter extends OncePerRequestFilter {

    private static final String HEADER = "X-Execution-Api-Key";

    private final ExecutionServerProperties properties;

    public ApiKeyAuthFilter(ExecutionServerProperties properties) {
        this.properties = properties;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        if ("/health".equals(request.getRequestURI())) {
            filterChain.doFilter(request, response);
            return;
        }

        String provided = request.getHeader(HEADER);
        String expected = properties.getApiKey();
        if (expected.isBlank() || provided == null || !constantTimeEquals(expected, provided)) {
            response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
            response.setContentType(MediaType.APPLICATION_JSON_VALUE);
            response.getWriter().write("{\"error\":\"UNAUTHORIZED\",\"message\":\"Missing or invalid " + HEADER + "\"}");
            return;
        }
        filterChain.doFilter(request, response);
    }

    // Avoids a timing side-channel on the key comparison -- java.security.MessageDigest.isEqual
    // is the JDK's own constant-time byte comparison.
    private boolean constantTimeEquals(String a, String b) {
        return java.security.MessageDigest.isEqual(
                a.getBytes(java.nio.charset.StandardCharsets.UTF_8), b.getBytes(java.nio.charset.StandardCharsets.UTF_8));
    }
}
