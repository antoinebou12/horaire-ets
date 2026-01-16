package me.imrashb.controller;

import io.micrometer.core.instrument.MeterRegistry;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.web.servlet.error.ErrorController;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import javax.servlet.RequestDispatcher;
import javax.servlet.http.HttpServletRequest;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;

@RestController
@Slf4j
public class CustomErrorController implements ErrorController {

    private final MeterRegistry registry;

    public CustomErrorController(MeterRegistry registry) {
        this.registry = registry;
    }

    @RequestMapping(value = "/error", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<Map<String, Object>> handleError(HttpServletRequest request) {
        Object status = request.getAttribute(RequestDispatcher.ERROR_STATUS_CODE);
        Object errorMessage = request.getAttribute(RequestDispatcher.ERROR_MESSAGE);
        Object requestUri = request.getAttribute(RequestDispatcher.ERROR_REQUEST_URI);

        HttpStatus httpStatus = HttpStatus.INTERNAL_SERVER_ERROR;
        String message = "An unexpected error occurred";

        if (status != null) {
            int statusCode = Integer.parseInt(status.toString());
            httpStatus = HttpStatus.valueOf(statusCode);

            // Customize messages based on status code
            switch (statusCode) {
                case 404:
                    message = "The requested resource was not found";
                    if (requestUri != null) {
                        log.warn("404 Not Found: {}", requestUri);
                    }
                    break;
                case 400:
                    message = "Bad request. Please check your request parameters";
                    break;
                case 403:
                    message = "Access forbidden. You don't have permission to access this resource";
                    break;
                case 405:
                    message = "Method not allowed. The HTTP method used is not supported for this endpoint";
                    break;
                case 500:
                    message = "Internal server error. Please try again later";
                    log.error("500 Internal Server Error: {} - {}", requestUri, errorMessage);
                    break;
                case 503:
                    message = "Service temporarily unavailable. Please try again later";
                    break;
                default:
                    if (errorMessage != null && !errorMessage.toString().isEmpty()) {
                        message = errorMessage.toString();
                    }
                    break;
            }
        }

        // Record metrics
        registry.counter("api_errors_total",
                "status", String.valueOf(httpStatus.value()),
                "endpoint", requestUri != null ? requestUri.toString() : "unknown"
        ).increment();

        // Build error response
        Map<String, Object> error = new HashMap<>();
        error.put("timestamp", Instant.now().toString());
        error.put("status", httpStatus.value());
        error.put("error", httpStatus.getReasonPhrase());
        error.put("message", message);
        error.put("path", requestUri != null ? requestUri.toString() : request.getRequestURI());

        return new ResponseEntity<>(error, httpStatus);
    }

    // Deprecated in Spring Boot 2.3+, but kept for compatibility
    public String getErrorPath() {
        return "/error";
    }
}
