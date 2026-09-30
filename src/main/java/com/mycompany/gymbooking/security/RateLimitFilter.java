package com.mycompany.gymbooking.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.mycompany.gymbooking.dto.ErrorResponse;
import com.mycompany.gymbooking.exception.TooManyRequestsException;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.time.Duration;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Per-IP rate limiting, with a stricter limit for /api/auth/** since login and verification codes
 * are the usual attack targets. Runs first in the security chain so floods are rejected before any
 * JWT, BCrypt or database work.
 *
 * Not a @Component: SecurityConfig places it in the chain, and a bean would also be registered as a
 * regular servlet filter and run twice.
 */
public class RateLimitFilter extends OncePerRequestFilter {

    private static final Duration WINDOW = Duration.ofMinutes(1);

    private final RateLimiter rateLimiter;
    private final ObjectMapper objectMapper;
    private final int authPerMinute;
    private final int generalPerMinute;

    public RateLimitFilter(RateLimiter rateLimiter, ObjectMapper objectMapper,
                           int authPerMinute, int generalPerMinute) {
        this.rateLimiter = rateLimiter;
        this.objectMapper = objectMapper;
        this.authPerMinute = authPerMinute;
        this.generalPerMinute = generalPerMinute;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {

        boolean isAuth = request.getRequestURI().startsWith("/api/auth/");
        int limit = isAuth ? authPerMinute : generalPerMinute;

        // Keyed on the socket address, not X-Forwarded-For: that header is client-controlled and
        // would let an attacker get a fresh bucket on every request.
        String key = (isAuth ? "auth:" : "general:") + request.getRemoteAddr();

        RateLimiter.Decision decision = rateLimiter.tryConsume(key, limit, WINDOW);
        if (decision.allowed()) {
            filterChain.doFilter(request, response);
            return;
        }

        long seconds = decision.retryAfterSeconds();
        response.setStatus(429);
        response.setHeader(HttpHeaders.RETRY_AFTER, String.valueOf(seconds));
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        objectMapper.writeValue(response.getOutputStream(), ErrorResponse.of(429, "RATE_LIMITED",
                "Too many requests. Try again in " + TooManyRequestsException.waitText(seconds) + "."));
    }
}
