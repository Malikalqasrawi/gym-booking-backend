package com.mycompany.gymbooking.controller;

import java.time.LocalDateTime;
import java.util.Map;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * The simplest possible endpoint. Open http://localhost:8080/api/health in your browser
 * to check the backend is running. The app also calls it to show "server offline" messages.
 */
@RestController
public class HealthController {

    @GetMapping("/api/health")
    public Map<String, Object> health() {
        return Map.of(
                "status", "UP",
                "time", LocalDateTime.now().toString()
        );
    }
}
