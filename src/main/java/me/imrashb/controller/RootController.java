package me.imrashb.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/")
@Tag(name = "Root", description = "Root endpoint providing API information and available endpoints")
public class RootController {

    @Value("${springdoc.swagger-ui.enabled:true}")
    private boolean swaggerEnabled;

    @GetMapping(produces = MediaType.APPLICATION_JSON_VALUE)
    @Operation(
            summary = "Get API information",
            description = "Returns API metadata including service name, version, status, and a list of all available endpoints."
    )
    public Map<String, Object> getRoot() {
        Map<String, Object> response = new HashMap<>();
        response.put("service", "HoraireETS API");
        response.put("version", "1.0.0");
        response.put("status", "running");
        
        Map<String, String> endpoints = new HashMap<>();
        endpoints.put("courses", "/cours");
        endpoints.put("search", "/cours/search?q=<query>&limit=20");
        endpoints.put("autocomplete", "/cours/autocomplete?q=<query>&limit=10");
        endpoints.put("sessions", "/sessions");
        endpoints.put("combinations", "/combinaisons");
        endpoints.put("statistics", "/statistics");
        endpoints.put("health", "/actuator/health");
        endpoints.put("metrics", "/actuator/prometheus");
        
        // Show Swagger endpoints if enabled
        if (swaggerEnabled) {
            endpoints.put("swagger-ui", "/swagger-ui/index.html");
            endpoints.put("swagger-ui-alt", "/swagger-ui.html");
            endpoints.put("api-docs", "/v3/api-docs");
        }
        
        response.put("endpoints", endpoints);
        return response;
    }

}
