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
 * Stateless JWT security with role-based access per API prefix. The Stripe webhook is public
 * because it is verified by its signature instead.
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
                // Tokens are sent in headers, not cookies, so CSRF doesn't apply
                .csrf(csrf -> csrf.disable())

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

                .exceptionHandling(ex -> ex
                        .authenticationEntryPoint(errorHandler)
                        .accessDeniedHandler(errorHandler))

                .addFilterBefore(jwtFilter, UsernamePasswordAuthenticationFilter.class)

                // Rate limit before JWT parsing so floods are rejected as cheaply as possible
                .addFilterBefore(rateLimitFilter, JwtAuthenticationFilter.class);

        return http.build();
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    public UserDetailsService userDetailsService(UserRepository userRepository) {
        return email -> userRepository.findByEmailIgnoreCase(email)
                .map(SecurityUser::new)
                .orElseThrow(() -> new UsernameNotFoundException("No user with email " + email));
    }
}
