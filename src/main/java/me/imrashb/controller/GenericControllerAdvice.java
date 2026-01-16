package me.imrashb.controller;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.servlet.NoHandlerFoundException;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;

@RestControllerAdvice
@Slf4j
public class GenericControllerAdvice {

    // Prometheus metrics
    private final Counter apiErrorsCounter;
    private final Counter apiErrorsByStatusCounter;
    private final MeterRegistry registry;

    public GenericControllerAdvice(Counter apiErrorsCounter, Counter apiErrorsByStatusCounter, MeterRegistry registry) {
        this.apiErrorsCounter = apiErrorsCounter;
        this.apiErrorsByStatusCounter = apiErrorsByStatusCounter;
        this.registry = registry;
    }

    @ExceptionHandler(NoHandlerFoundException.class)
    public ResponseEntity<Map<String, Object>> handleNoHandlerFound(NoHandlerFoundException ex) {
        log.warn("No handler found for {} {}", ex.getHttpMethod(), ex.getRequestURL());
        
        // Record metrics
        registry.counter("api_errors_total",
                "exception_type", ex.getClass().getSimpleName(),
                "endpoint", ex.getRequestURL()
        ).increment();
        registry.counter("api_errors_by_status_total",
                "status", String.valueOf(HttpStatus.NOT_FOUND.value())
        ).increment();
        
        Map<String, Object> error = new HashMap<>();
        error.put("timestamp", Instant.now().toString());
        error.put("status", HttpStatus.NOT_FOUND.value());
        error.put("error", "Not Found");
        error.put("message", "The requested resource was not found");
        error.put("path", ex.getRequestURL());
        
        return new ResponseEntity<>(error, HttpStatus.NOT_FOUND);
    }

    @ExceptionHandler(MissingServletRequestParameterException.class)
    public ResponseEntity<Map<String, String>> handleMissingParams(MissingServletRequestParameterException ex) {
        // Security: Log detailed error server-side, return generic message to client
        log.warn("Missing request parameter: {} of type {}", ex.getParameterName(), ex.getParameterType(), ex);
        
        // Record metrics
        registry.counter("api_errors_total",
                "exception_type", ex.getClass().getSimpleName(),
                "endpoint", "unknown"
        ).increment();
        registry.counter("api_errors_by_status_total",
                "status", String.valueOf(HttpStatus.BAD_REQUEST.value())
        ).increment();
        
        Map<String, String> error = new HashMap<>();
        error.put("error", "Missing required parameter");
        error.put("message", "Required parameter '" + ex.getParameterName() + "' is missing");
        return new ResponseEntity<>(error, HttpStatus.BAD_REQUEST);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<Map<String, Object>> handleGenericException(Exception ex) {
        // Security: Catch-all handler to prevent information leakage
        log.error("Unexpected error occurred", ex);
        
        // Record metrics
        registry.counter("api_errors_total",
                "exception_type", ex.getClass().getSimpleName(),
                "endpoint", "unknown"
        ).increment();
        registry.counter("api_errors_by_status_total",
                "status", String.valueOf(HttpStatus.INTERNAL_SERVER_ERROR.value())
        ).increment();
        
        Map<String, Object> error = new HashMap<>();
        error.put("timestamp", Instant.now().toString());
        error.put("status", HttpStatus.INTERNAL_SERVER_ERROR.value());
        error.put("error", "Internal Server Error");
        error.put("message", "An unexpected error occurred. Please try again later.");
        return new ResponseEntity<>(error, HttpStatus.INTERNAL_SERVER_ERROR);
    }

}
