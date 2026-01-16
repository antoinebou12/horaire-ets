package me.imrashb.controller;

import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.Map;

@RestController
@RequestMapping("/")
public class RootController {

    // Version from pom.xml - update this when version changes
    private static final String API_VERSION = "1.0.0";

    @GetMapping(produces = MediaType.APPLICATION_JSON_VALUE)
    public Map<String, Object> getRoot() {
        Map<String, Object> response = new LinkedHashMap<>();
        response.put("service", "HoraireETS API");
        response.put("version", API_VERSION);
        response.put("status", "running");

        // Organize endpoints by category
        Map<String, Object> endpoints = new LinkedHashMap<>();

        // Courses endpoints
        Map<String, String> coursesEndpoints = new LinkedHashMap<>();
        coursesEndpoints.put("list", "/cours?programmes=LOG,INF");
        coursesEndpoints.put("search", "/cours/search?query=<query>&algorithm=hybrid&limit=20&programmes=LOG,INF&minCredits=3&maxCredits=4");
        coursesEndpoints.put("autocomplete", "/cours/autocomplete?query=<query>&limit=10&programmes=LOG&minCredits=3");
        coursesEndpoints.put("ics", "/cours/{sigle}/{session}/ics?startDate=2024-01-08&endDate=2024-04-26");
        endpoints.put("courses", coursesEndpoints);

        // Sessions endpoints
        Map<String, String> sessionsEndpoints = new LinkedHashMap<>();
        sessionsEndpoints.put("list", "/sessions");
        sessionsEndpoints.put("courses", "/sessions/{session}?programmes=LOG");
        sessionsEndpoints.put("programmes", "/sessions/programmes?session=20231");
        endpoints.put("sessions", sessionsEndpoints);

        // Combinations endpoints
        Map<String, String> combinationsEndpoints = new LinkedHashMap<>();
        combinationsEndpoints.put("generate", "/combinaisons?<params>");
        combinationsEndpoints.put("sorters", "/combinaisons/sort");
        combinationsEndpoints.put("image", "/combinaisons/{id}?theme=dark");
        combinationsEndpoints.put("byIds", "/combinaisons/id?ids=<ids>");
        combinationsEndpoints.put("ics", "/combinaisons/{id}/ics?startDate=2024-01-08&endDate=2024-04-26");
        endpoints.put("combinations", combinationsEndpoints);

        // Statistics endpoints
        Map<String, String> statisticsEndpoints = new LinkedHashMap<>();
        statisticsEndpoints.put("get", "/statistics");
        endpoints.put("statistics", statisticsEndpoints);

        // System endpoints
        Map<String, String> systemEndpoints = new LinkedHashMap<>();
        systemEndpoints.put("health", "/actuator/health");
        systemEndpoints.put("metrics", "/actuator/prometheus");

        endpoints.put("system", systemEndpoints);

        response.put("endpoints", endpoints);
        return response;
    }
}
