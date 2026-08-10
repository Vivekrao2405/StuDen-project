package com.studen.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedDeque;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.lang.NonNull;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * A coarse, per-IP sliding-window limit on the unauthenticated auth endpoints (register/login),
 * independent of {@link LoginAttemptService}'s per-email lockout — this guards against
 * credential-stuffing/registration-spam spread across many different email addresses from one
 * source, which per-email tracking alone wouldn't catch.
 *
 * In-memory, so it has the same multi-instance caveat as {@link LoginAttemptService} (see its
 * class doc) — each backend instance enforces its own independent counter. request.getRemoteAddr()
 * only reflects the real client IP (rather than a reverse proxy's) when
 * server.forward-headers-strategy=framework is set, which trusts the platform's forwarded-for
 * headers — see application.yml.
 */
@Component
public class AuthRateLimitFilter extends OncePerRequestFilter {

    private final int maxRequestsPerWindow;
    private final Duration window;
    private final Map<String, ConcurrentLinkedDeque<Instant>> requestsByIp = new ConcurrentHashMap<>();

    public AuthRateLimitFilter(
            @Value("${app.security.auth-rate-limit.max-requests:30}") int maxRequestsPerWindow,
            @Value("${app.security.auth-rate-limit.window-seconds:60}") long windowSeconds) {
        this.maxRequestsPerWindow = maxRequestsPerWindow;
        this.window = Duration.ofSeconds(windowSeconds);
    }

    @Override
    protected boolean shouldNotFilter(@NonNull HttpServletRequest request) {
        String path = request.getRequestURI();
        return !("POST".equalsIgnoreCase(request.getMethod())
                && (path.endsWith("/auth/login") || path.endsWith("/auth/register")));
    }

    @Override
    protected void doFilterInternal(@NonNull HttpServletRequest request, @NonNull HttpServletResponse response,
            @NonNull FilterChain filterChain) throws ServletException, IOException {

        String ip = request.getRemoteAddr();
        ConcurrentLinkedDeque<Instant> timestamps = requestsByIp.computeIfAbsent(ip, k -> new ConcurrentLinkedDeque<>());
        Instant now = Instant.now();
        Instant windowStart = now.minus(window);

        synchronized (timestamps) {
            while (!timestamps.isEmpty() && timestamps.peekFirst().isBefore(windowStart)) {
                timestamps.pollFirst();
            }
            if (timestamps.size() >= maxRequestsPerWindow) {
                response.setStatus(429);
                response.setContentType("application/json");
                response.getWriter().write(
                        "{\"error\":\"TOO_MANY_REQUESTS\",\"message\":\"Too many requests. Please try again later.\"}");
                return;
            }
            timestamps.addLast(now);
        }

        filterChain.doFilter(request, response);
    }
}
