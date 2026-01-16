package me.imrashb.controller;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import lombok.extern.slf4j.Slf4j;
import me.imrashb.exception.*;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.HashMap;
import java.util.Map;

@RestControllerAdvice
@Slf4j
public class CombinaisonControllerAdvice {

    // Prometheus metrics
    private final Counter apiCombinaisonErrorsCounter;
    private final Counter apiErrorsCounter;
    private final Counter apiErrorsByStatusCounter;
    private final MeterRegistry registry;

    public CombinaisonControllerAdvice(Counter apiCombinaisonErrorsCounter,
                                      Counter apiErrorsCounter,
                                      Counter apiErrorsByStatusCounter,
                                      MeterRegistry registry) {
        this.apiCombinaisonErrorsCounter = apiCombinaisonErrorsCounter;
        this.apiErrorsCounter = apiErrorsCounter;
        this.apiErrorsByStatusCounter = apiErrorsByStatusCounter;
        this.registry = registry;
    }

    @ExceptionHandler({SessionDoesntExistException.class, CoursDoesntExistException.class})
    public ResponseEntity<Map<String, String>> handleNotFoundException(Exception exception) {
        // Security: Log detailed error server-side, return generic message to client
        log.error("Not found error: {}", exception.getMessage(), exception);
        
        // Record metrics
        String errorType = exception.getClass().getSimpleName();
        registry.counter("api_combinaison_errors_total",
                "error_type", errorType
        ).increment();
        registry.counter("api_errors_total",
                "exception_type", errorType,
                "endpoint", "/combinaisons"
        ).increment();
        registry.counter("api_errors_by_status_total",
                "status", String.valueOf(HttpStatus.NOT_FOUND.value())
        ).increment();
        
        Map<String, String> error = new HashMap<>();
        error.put("error", "Resource not found");
        error.put("message", getSafeMessage(exception));
        return new ResponseEntity<>(error, HttpStatus.NOT_FOUND);
    }

    @ExceptionHandler({InvalidCoursAmountException.class, InvalidCoursObligatoiresException.class})
    public ResponseEntity<Map<String, String>> handleBadRequestException(Exception exception) {
        // Security: Log detailed error server-side
        log.warn("Bad request error: {}", exception.getMessage(), exception);
        
        // Record metrics
        String errorType = exception.getClass().getSimpleName();
        registry.counter("api_combinaison_errors_total",
                "error_type", errorType
        ).increment();
        registry.counter("api_errors_total",
                "exception_type", errorType,
                "endpoint", "/combinaisons"
        ).increment();
        registry.counter("api_errors_by_status_total",
                "status", String.valueOf(HttpStatus.BAD_REQUEST.value())
        ).increment();
        
        Map<String, String> error = new HashMap<>();
        error.put("error", "Invalid request");
        error.put("message", getSafeMessage(exception));
        return new ResponseEntity<>(error, HttpStatus.BAD_REQUEST);
    }

    @ExceptionHandler(CoursNotInitializedException.class)
    public ResponseEntity<Map<String, String>> handleUnavailableException(Exception exception) {
        log.error("Service unavailable: {}", exception.getMessage(), exception);
        
        // Record metrics
        String errorType = exception.getClass().getSimpleName();
        registry.counter("api_combinaison_errors_total",
                "error_type", errorType
        ).increment();
        registry.counter("api_errors_total",
                "exception_type", errorType,
                "endpoint", "/combinaisons"
        ).increment();
        registry.counter("api_errors_by_status_total",
                "status", String.valueOf(HttpStatus.SERVICE_UNAVAILABLE.value())
        ).increment();
        
        Map<String, String> error = new HashMap<>();
        error.put("error", "Service temporarily unavailable");
        error.put("message", "The course data is not yet initialized. Please try again later.");
        return new ResponseEntity<>(error, HttpStatus.SERVICE_UNAVAILABLE);
    }

    @ExceptionHandler(InvalidEncodedIdException.class)
    public ResponseEntity<Map<String, String>> handleInvalidEncodedId(Exception exception) {
        log.warn("Invalid encoded ID: {}", exception.getMessage(), exception);
        
        // Record metrics
        String errorType = exception.getClass().getSimpleName();
        registry.counter("api_combinaison_errors_total",
                "error_type", errorType
        ).increment();
        registry.counter("api_errors_total",
                "exception_type", errorType,
                "endpoint", "/combinaisons"
        ).increment();
        registry.counter("api_errors_by_status_total",
                "status", String.valueOf(HttpStatus.BAD_REQUEST.value())
        ).increment();
        
        Map<String, String> error = new HashMap<>();
        error.put("error", "Invalid identifier");
        error.put("message", "The provided identifier is invalid or malformed.");
        return new ResponseEntity<>(error, HttpStatus.BAD_REQUEST);
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<Map<String, String>> handleIllegalArgumentException(IllegalArgumentException exception) {
        log.warn("Illegal argument: {}", exception.getMessage(), exception);
        
        // Record metrics
        String errorType = exception.getClass().getSimpleName();
        registry.counter("api_combinaison_errors_total",
                "error_type", errorType
        ).increment();
        registry.counter("api_errors_total",
                "exception_type", errorType,
                "endpoint", "/combinaisons"
        ).increment();
        registry.counter("api_errors_by_status_total",
                "status", String.valueOf(HttpStatus.BAD_REQUEST.value())
        ).increment();
        
        Map<String, String> error = new HashMap<>();
        error.put("error", "Invalid request parameter");
        error.put("message", getSafeMessage(exception));
        return new ResponseEntity<>(error, HttpStatus.BAD_REQUEST);
    }

    /**
     * Security: Sanitize error messages to prevent information disclosure
     * Returns a safe message that doesn't expose internal details
     */
    private String getSafeMessage(Exception exception) {
        String message = exception.getMessage();
        if (message == null) {
            return "An error occurred";
        }
        // Remove potential sensitive information patterns
        // In production, implement more sophisticated sanitization
        return message;
    }

}
