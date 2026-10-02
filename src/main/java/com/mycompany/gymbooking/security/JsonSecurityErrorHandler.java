package com.mycompany.gymbooking.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.mycompany.gymbooking.dto.ErrorResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.stereotype.Component;

/**
 * Writes 401 and 403 responses from the security filter chain as ErrorResponse JSON. These errors
 * occur before the controllers, so GlobalExceptionHandler never sees them.
 */
@Component
public class JsonSecurityErrorHandler implements AuthenticationEntryPoint, AccessDeniedHandler {

    private final ObjectMapper objectMapper;

    public JsonSecurityErrorHandler(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @Override
    public void commence(HttpServletRequest request, HttpServletResponse response,
                         AuthenticationException authException) throws IOException {
        // A token was sent but rejected: it expired, is invalid, or its account was deactivated.
        String auth = request.getHeader(HttpHeaders.AUTHORIZATION);
        boolean sentToken = auth != null && auth.startsWith("Bearer ");
        write(response, 401, "UNAUTHORIZED",
                sentToken ? "Your session has ended. Please log in again." : "Please log in first");
    }

    @Override
    public void handle(HttpServletRequest request, HttpServletResponse response,
                       AccessDeniedException accessDeniedException) throws IOException {
        write(response, 403, "FORBIDDEN", "You don't have permission to do this");
    }

    private void write(HttpServletResponse response, int status, String code, String message) throws IOException {
        response.setStatus(status);
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        objectMapper.writeValue(response.getOutputStream(), ErrorResponse.of(status, code, message));
    }
}
