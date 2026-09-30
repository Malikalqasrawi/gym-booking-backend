package com.mycompany.gymbooking.security;

import com.mycompany.gymbooking.repository.UserRepository;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Runs ONCE before every request reaches a controller.
 *
 *   1. Look for the header   Authorization: Bearer <token>
 *   2. Ask TokenService which email is inside the token
 *   3. Load that user from the database
 *   4. Tell Spring Security "this request is from user X with role Y"
 *
 * If there's no token or it's invalid, we do nothing. Protected endpoints will then answer 401.
 *
 * Not a @Component on purpose: SecurityConfig creates it and puts it inside the security chain,
 * so Spring Boot doesn't also register it a second time as a normal web filter.
 */
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private static final String BEARER_PREFIX = "Bearer ";

    private final TokenService tokenService;
    private final UserRepository userRepository;

    public JwtAuthenticationFilter(TokenService tokenService, UserRepository userRepository) {
        this.tokenService = tokenService;
        this.userRepository = userRepository;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {

        String header = request.getHeader("Authorization");

        if (header != null && header.startsWith(BEARER_PREFIX)) {
            String token = header.substring(BEARER_PREFIX.length());

            tokenService.readEmail(token)
                    .flatMap(userRepository::findByEmailIgnoreCase)
                    .filter(user -> user.isVerified())
                    .ifPresent(user -> {
                        SecurityUser principal = new SecurityUser(user);
                        var authentication = new UsernamePasswordAuthenticationToken(
                                principal, null, principal.getAuthorities());
                        authentication.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
                        SecurityContextHolder.getContext().setAuthentication(authentication);
                    });
        }

        // Pass the request on to the next filter / the controller
        filterChain.doFilter(request, response);
    }
}
