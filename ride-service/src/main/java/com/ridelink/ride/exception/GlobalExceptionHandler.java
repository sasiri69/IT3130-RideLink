package com.ridelink.ride.exception;

import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

/**
 * Global exception handler translating application and domain exceptions into
 * structured JSON responses with appropriate HTTP status codes.
 */
@RestControllerAdvice
@Slf4j
public class GlobalExceptionHandler {

    @ExceptionHandler(RideNotFoundException.class)
    public ResponseEntity<Map<String, Object>> handleRideNotFound(RideNotFoundException ex) {
        return buildErrorResponse(HttpStatus.NOT_FOUND, "Not Found", ex.getMessage());
    }

    @ExceptionHandler(NoAvailableDriverException.class)
    public ResponseEntity<Map<String, Object>> handleNoAvailableDriver(NoAvailableDriverException ex) {
        return buildErrorResponse(HttpStatus.NOT_FOUND, "No Available Driver", ex.getMessage());
    }

    @ExceptionHandler(InvalidStateTransitionException.class)
    public ResponseEntity<Map<String, Object>> handleInvalidStateTransition(InvalidStateTransitionException ex) {
        return buildErrorResponse(HttpStatus.BAD_REQUEST, "Invalid State Transition", ex.getMessage());
    }

    @ExceptionHandler(InvalidPassengerAccountException.class)
    public ResponseEntity<Map<String, Object>> handleInvalidPassengerAccount(InvalidPassengerAccountException ex) {
        return buildErrorResponse(HttpStatus.BAD_REQUEST, "Invalid Passenger Account", ex.getMessage());
    }

    @ExceptionHandler(UnauthorizedRideAccessException.class)
    public ResponseEntity<Map<String, Object>> handleUnauthorizedRideAccess(UnauthorizedRideAccessException ex) {
        return buildErrorResponse(HttpStatus.FORBIDDEN, "Forbidden", ex.getMessage());
    }

    @ExceptionHandler(DriverServiceUnavailableException.class)
    public ResponseEntity<Map<String, Object>> handleDriverServiceUnavailable(DriverServiceUnavailableException ex) {
        log.error("Driver Service communication error: {}", ex.getMessage());
        return buildErrorResponse(HttpStatus.SERVICE_UNAVAILABLE, "Service Unavailable", ex.getMessage());
    }

    @ExceptionHandler(AccountServiceUnavailableException.class)
    public ResponseEntity<Map<String, Object>> handleAccountServiceUnavailable(AccountServiceUnavailableException ex) {
        log.error("Account Service communication error: {}", ex.getMessage());
        return buildErrorResponse(HttpStatus.SERVICE_UNAVAILABLE, "Service Unavailable", ex.getMessage());
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<Map<String, Object>> handleValidationExceptions(MethodArgumentNotValidException ex) {
        Map<String, String> fieldErrors = new HashMap<>();
        for (FieldError error : ex.getBindingResult().getFieldErrors()) {
            fieldErrors.put(error.getField(), error.getDefaultMessage());
        }

        Map<String, Object> error = new HashMap<>();
        error.put("timestamp", LocalDateTime.now());
        error.put("status", HttpStatus.BAD_REQUEST.value());
        error.put("error", "Bad Request");
        error.put("message", "Input validation failed. Please check submitted fields.");
        error.put("validationErrors", fieldErrors);

        return new ResponseEntity<>(error, HttpStatus.BAD_REQUEST);
    }

    @ExceptionHandler(MissingServletRequestParameterException.class)
    public ResponseEntity<Map<String, Object>> handleMissingParam(MissingServletRequestParameterException ex) {
        return buildErrorResponse(
                HttpStatus.BAD_REQUEST,
                "Missing Parameter",
                "Required query parameter '" + ex.getParameterName() + "' is missing."
        );
    }

    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<Map<String, Object>> handleTypeMismatch(MethodArgumentTypeMismatchException ex) {
        return buildErrorResponse(
                HttpStatus.BAD_REQUEST,
                "Type Mismatch",
                "Parameter '" + ex.getName() + "' has invalid format or unrecognized value."
        );
    }

    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<Map<String, Object>> handleAccessDenied(AccessDeniedException ex) {
        String msg = (ex.getMessage() != null && !ex.getMessage().isBlank()) ? ex.getMessage() : "Access denied: insufficient permissions.";
        return buildErrorResponse(HttpStatus.FORBIDDEN, "Forbidden", msg);
    }

    @ExceptionHandler(AuthenticationException.class)
    public ResponseEntity<Map<String, Object>> handleAuthentication(AuthenticationException ex) {
        return buildErrorResponse(HttpStatus.UNAUTHORIZED, "Unauthorized", "Authentication token is missing or invalid.");
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<Map<String, Object>> handleIllegalArgument(IllegalArgumentException ex) {
        return buildErrorResponse(HttpStatus.BAD_REQUEST, "Bad Request", ex.getMessage());
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<Map<String, Object>> handleGeneralException(Exception ex) {
        log.error("Unhandled internal server exception", ex);
        return buildErrorResponse(
                HttpStatus.INTERNAL_SERVER_ERROR,
                "Internal Server Error",
                "An unexpected server error occurred: " + ex.getMessage()
        );
    }

    private ResponseEntity<Map<String, Object>> buildErrorResponse(HttpStatus status, String errorTitle, String message) {
        Map<String, Object> error = new HashMap<>();
        error.put("timestamp", LocalDateTime.now());
        error.put("status", status.value());
        error.put("error", errorTitle);
        error.put("message", message);
        return new ResponseEntity<>(error, status);
    }
}
