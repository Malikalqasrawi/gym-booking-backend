package com.mycompany.gymbooking.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.mycompany.gymbooking.repository.UserRepository;
import com.mycompany.gymbooking.security.JsonSecurityErrorHandler;
import com.mycompany.gymbooking.security.JwtAuthenticationFilter;
import com.mycompany.gymbooking.security.RateLimitFilter;
import com.mycompany.gymbooking.security.RateLimiter;
import com.mycompany.gymbooking.security.SecurityUser;
import com.mycompany.gymbooking.security.TokenService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

/**
 * Who is allowed to call which endpoint.
 *
 *   /api/auth/**     → everyone (sign up, verify, login)
 *   /api/health      → everyone
 *   POST /api/payments/stripe/webhook → everyone (it's Stripe; checked by its signature instead of a login)
 *   GET  /api/branches/**, /api/trainers/**  → any logged-in user
 *   POST/PUT/DELETE /api/branches/**  → only ADMIN
 *   /api/admin/**    → only ADMIN      (Stage 5)
 *   /api/trainer/**  → only TRAINER    (answer requests, see schedule)
 *   /api/bookings/** → only MEMBER     (request, list, cancel, pay)
 *   everything else  → any logged-in user
 *
 * Every request first passes RateLimitFilter (too many per minute from one IP → 429).
 */
@Configuration
@EnableWebSecurity
public class SecurityConfig {

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http,
                                                   TokenService tokenService,
                                                   UserRepository userRepository,
                                                   JsonSecurityErrorHandler errorHandler,
                                                   RateLimiter rateLimiter,
                                                   ObjectMapper objectMapper,
                                                   @Value("${app.rate-limit.auth-per-minute}") int authPerMinute,
                                                   @Value("${app.rate-limit.general-per-minute}") int generalPerMinute)
            throws Exception {

        JwtAuthenticationFilter jwtFilter = new JwtAuthenticationFilter(tokenService, userRepository);
        RateLimitFilter rateLimitFilter = new RateLimitFilter(rateLimiter, objectMapper, authPerMinute, generalPerMinute);

        http
                // CSRF protection is for browser cookie logins; we use tokens in headers instead
                .csrf(csrf -> csrf.disable())

                // No server-side sessions: every request must carry its own token
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))

                .authorizeHttpRequests(auth -> auth
                        .requestMatchers("/api/auth/**", "/api/health", "/error").permitAll()
                        .requestMatchers(HttpMethod.POST, "/api/payments/stripe/webhook").permitAll()
                        .requestMatchers(HttpMethod.POST, "/api/branches/**").hasRole("ADMIN")
                        .requestMatchers(HttpMethod.PUT, "/api/branches/**").hasRole("ADMIN")
                        .requestMatchers(HttpMethod.DELETE, "/api/branches/**").hasRole("ADMIN")
                        .requestMatchers("/api/admin/**").hasRole("ADMIN")
                        .requestMatchers("/api/trainer/**").hasRole("TRAINER")
                        .requestMatchers("/api/bookings/**").hasRole("MEMBER")
                        .anyRequest().authenticated())

                // Return our JSON errors for 401 / 403
                .exceptionHandling(ex -> ex
                        .authenticationEntryPoint(errorHandler)
                        .accessDeniedHandler(errorHandler))

                // Check the JWT before Spring's own login filter
                .addFilterBefore(jwtFilter, UsernamePasswordAuthenticationFilter.class)

                // Count requests even earlier, before the JWT check: floods are stopped as cheaply as possible
                .addFilterBefore(rateLimitFilter, JwtAuthenticationFilter.class);

        return http.build();
    }

    /** BCrypt turns "Secret123" into "$2a$10$Xy..." (one-way: it can't be turned back). */
    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    /** Tells Spring Security how to find a user by email (uses our SecurityUser adapter). */
    @Bean
    public UserDetailsService userDetailsService(UserRepository userRepository) {
        return email -> userRepository.findByEmailIgnoreCase(email)
                .map(SecurityUser::new)
                .orElseThrow(() -> new UsernameNotFoundException("No user with email " + email));
    }
}
