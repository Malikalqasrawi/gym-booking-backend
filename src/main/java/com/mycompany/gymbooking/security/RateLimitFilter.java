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
 * Counts every request per IP address and answers 429 when someone sends too many.
 *
 * It runs FIRST in the security chain (before the JWT check, before BCrypt, before the database),
 * so a flood of requests is stopped as cheaply as possible.
 *
 * Two rules, each with its own bucket:
 *   /api/auth/**    → app.rate-limit.auth-per-minute     (login, sign up, codes: what attackers target)
 *   everything else → app.rate-limit.general-per-minute
 *
 * Not a @Component on purpose: SecurityConfig creates it and puts it in the right place in the chain
 * (a @Component filter would ALSO be added by Spring Boot on its own and run twice).
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

        // getRemoteAddr() = the IP address that actually connected to us.
        // We do NOT read the "X-Forwarded-For" header: anyone can put any IP in it,
        // so an attacker would get a brand-new bucket on every request.
        String key = (isAuth ? "auth:" : "general:") + request.getRemoteAddr();

        RateLimiter.Decision decision = rateLimiter.tryConsume(key, limit, WINDOW);
        if (decision.allowed()) {
            filterChain.doFilter(request, response);   // carry on to the next filter / the controller
            return;
        }

        // Blocked: answer right here. The controller never runs.
        long seconds = decision.retryAfterSeconds();
        response.setStatus(429);
        response.setHeader(HttpHeaders.RETRY_AFTER, String.valueOf(seconds));
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        objectMapper.writeValue(response.getOutputStream(), ErrorResponse.of(429, "RATE_LIMITED",
                "Too many requests. Try again in " + TooManyRequestsException.waitText(seconds) + "."));
    }
}
