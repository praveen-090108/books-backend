package com.intelliatech.app.controller;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/health")
@RequiredArgsConstructor
public class HealthController {

    public static final String API_COMPATIBILITY_VERSION = "2026-07-20-payment-received-v1";

    private final JdbcTemplate jdbcTemplate;

    @GetMapping
    public Map<String, Object> health() {
        Integer databaseResult = jdbcTemplate.queryForObject("SELECT 1", Integer.class);
        Map<String, Object> response = new LinkedHashMap<>();
        response.put("status", "UP");
        response.put("application", "intelliatech-books");
        response.put("apiCompatibility", API_COMPATIBILITY_VERSION);
        response.put("buildFingerprint", System.getenv().getOrDefault("API_BUILD_FINGERPRINT", "development"));
        response.put("database", databaseResult != null && databaseResult == 1 ? "UP" : "DOWN");
        response.put("timestamp", Instant.now().toString());
        return response;
    }
}
