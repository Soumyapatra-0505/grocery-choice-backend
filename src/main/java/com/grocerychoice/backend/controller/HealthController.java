package com.grocerychoice.backend.controller;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import javax.sql.DataSource;
import java.sql.Connection;
import java.util.LinkedHashMap;
import java.util.Map;

@RestController
@RequestMapping("/api")
public class HealthController {

    @Autowired(required = false)
    private DataSource dataSource;

    @GetMapping("/health")
    public ResponseEntity<Map<String, String>> checkHealth() {
        Map<String, String> response = new LinkedHashMap<>();
        response.put("status", "UP");
        response.put("service", "Grocery Choice Backend");
        return ResponseEntity.ok(response);
    }

    @GetMapping("/health/db")
    public ResponseEntity<Map<String, Object>> checkDatabaseHealth() {
        Map<String, Object> response = new LinkedHashMap<>();
        if (dataSource == null) {
            response.put("database", "DOWN");
            response.put("error", "DataSource bean is not available");
            return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).body(response);
        }

        try (Connection connection = dataSource.getConnection()) {
            boolean isValid = connection.isValid(2);
            response.put("database", isValid ? "CONNECTED" : "UNAVAILABLE");
            response.put("catalog", connection.getCatalog());
            response.put("productName", connection.getMetaData().getDatabaseProductName());
            response.put("productVersion", connection.getMetaData().getDatabaseProductVersion());
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            response.put("database", "DISCONNECTED");
            response.put("error", e.getMessage());
            return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).body(response);
        }
    }

    @GetMapping("/health/memory")
    public ResponseEntity<Map<String, Object>> checkMemory() {
        Runtime runtime = Runtime.getRuntime();
        long maxMemory = runtime.maxMemory();
        long totalMemory = runtime.totalMemory();
        long freeMemory = runtime.freeMemory();
        long usedMemory = totalMemory - freeMemory;

        Map<String, Object> memory = new LinkedHashMap<>();
        memory.put("status", "UP");
        memory.put("usedMemoryMb", usedMemory / (1024 * 1024));
        memory.put("freeMemoryMb", freeMemory / (1024 * 1024));
        memory.put("totalMemoryMb", totalMemory / (1024 * 1024));
        memory.put("maxMemoryMb", maxMemory / (1024 * 1024));
        memory.put("availableProcessors", runtime.availableProcessors());
        return ResponseEntity.ok(memory);
    }
}
