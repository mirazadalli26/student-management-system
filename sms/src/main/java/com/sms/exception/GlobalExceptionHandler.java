package com.sms.exception;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataAccessException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.AuthenticationException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.ConstraintViolationException;

/**
 * Centralized exception handling for the REST API. Every handled exception is
 * translated into the safe {@link ErrorResponse} JSON envelope with an
 * appropriate HTTP status. Raw exceptions, stack traces, and SQL/data-store
 * details are never exposed to the client (R10.3).
 *
 * <p>Status mapping:</p>
 * <ul>
 *   <li>Bean Validation / business-rule validation &rarr; 400</li>
 *   <li>Duplicate username &rarr; 409</li>
 *   <li>Resource not found &rarr; 404</li>
 *   <li>Any unhandled/data-store failure &rarr; 500 (generic message)</li>
 * </ul>
 *
 * <p>Satisfies R5.5, R7.4, R8.4, R8.5, R9.6, R10.3.</p>
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    /**
     * Bean Validation failures on {@code @Valid} request bodies (missing/blank
     * required fields, bad email/mobile/length/fee-range constraints). Returns
     * 400 with a field &rarr; message map. (R5.3, R5.4, R5.7-R5.9, R7.5, R7.6)
     */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErrorResponse> handleMethodArgumentNotValid(MethodArgumentNotValidException ex) {
        Map<String, String> fieldErrors = new LinkedHashMap<>();
        for (FieldError fieldError : ex.getBindingResult().getFieldErrors()) {
            // Keep the first message per field for a stable, readable response.
            fieldErrors.putIfAbsent(fieldError.getField(), fieldError.getDefaultMessage());
        }
        ErrorResponse body = new ErrorResponse(
                Instant.now(),
                HttpStatus.BAD_REQUEST.value(),
                "Validation Failed",
                "One or more fields are invalid.",
                fieldErrors);
        return ResponseEntity.badRequest().body(body);
    }

    /**
     * Constraint violations raised outside of request-body binding (e.g. on
     * validated path variables/params or method-level constraints). Returns 400
     * with a field &rarr; message map.
     */
    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<ErrorResponse> handleConstraintViolation(ConstraintViolationException ex) {
        Map<String, String> fieldErrors = new LinkedHashMap<>();
        if (ex.getConstraintViolations() != null) {
            for (ConstraintViolation<?> violation : ex.getConstraintViolations()) {
                String field = violation.getPropertyPath() == null ? "" : violation.getPropertyPath().toString();
                fieldErrors.putIfAbsent(field, violation.getMessage());
            }
        }
        ErrorResponse body = new ErrorResponse(
                Instant.now(),
                HttpStatus.BAD_REQUEST.value(),
                "Validation Failed",
                "One or more fields are invalid.",
                fieldErrors);
        return ResponseEntity.badRequest().body(body);
    }

    /**
     * Business-rule validation performed in the service layer, such as
     * paidFees &gt; totalFees. The service throws {@link IllegalArgumentException}
     * for these cases. Returns 400. (R5.6, R7.7)
     */
    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ErrorResponse> handleIllegalArgument(IllegalArgumentException ex) {
        ErrorResponse body = new ErrorResponse(
                HttpStatus.BAD_REQUEST.value(),
                "Validation Failed",
                ex.getMessage());
        return ResponseEntity.badRequest().body(body);
    }

    /**
     * Failed credential verification during login: unknown username or a
     * password mismatch surfaced by the {@code AuthenticationManager}. The
     * client receives a safe generic message with no indication of which part
     * of the credentials was wrong. Returns 401. (R2.2)
     */
    @ExceptionHandler(AuthenticationException.class)
    public ResponseEntity<ErrorResponse> handleAuthentication(AuthenticationException ex) {
        log.debug("Authentication failed: {}", ex.getMessage());
        ErrorResponse body = new ErrorResponse(
                HttpStatus.UNAUTHORIZED.value(),
                "Unauthorized",
                "Invalid username or password.");
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(body);
    }

    /**
     * Login attempt against an account that is currently locked out after too
     * many consecutive failures. Returns 401. (R2.8)
     */
    @ExceptionHandler(AccountLockedException.class)
    public ResponseEntity<ErrorResponse> handleAccountLocked(AccountLockedException ex) {
        ErrorResponse body = new ErrorResponse(
                HttpStatus.UNAUTHORIZED.value(),
                "Unauthorized",
                ex.getMessage());
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(body);
    }

    /**
     * Duplicate username on create/update. Returns 409. (R5.5)
     */
    @ExceptionHandler(UsernameConflictException.class)
    public ResponseEntity<ErrorResponse> handleUsernameConflict(UsernameConflictException ex) {
        ErrorResponse body = new ErrorResponse(
                HttpStatus.CONFLICT.value(),
                "Conflict",
                ex.getMessage());
        return ResponseEntity.status(HttpStatus.CONFLICT).body(body);
    }

    /**
     * Requested record does not exist. Returns 404. (R7.4, R8.4, R9.6)
     */
    @ExceptionHandler(ResourceNotFoundException.class)
    public ResponseEntity<ErrorResponse> handleResourceNotFound(ResourceNotFoundException ex) {
        ErrorResponse body = new ErrorResponse(
                HttpStatus.NOT_FOUND.value(),
                "Not Found",
                ex.getMessage());
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(body);
    }

    /**
     * Data-store/persistence failures (e.g. a failed delete). Logged server-side
     * with full detail, but the client receives only a safe generic message with
     * no stack trace or SQL exposed. Returns 500. (R8.5, R10.3)
     */
    @ExceptionHandler(DataAccessException.class)
    public ResponseEntity<ErrorResponse> handleDataAccess(DataAccessException ex) {
        log.error("Data-store failure while processing request", ex);
        ErrorResponse body = new ErrorResponse(
                HttpStatus.INTERNAL_SERVER_ERROR.value(),
                "Internal Server Error",
                "Something went wrong on our end.");
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(body);
    }

    /**
     * Catch-all for any otherwise-unhandled exception. Logged server-side; the
     * client receives only a safe generic message. Returns 500. (R10.3)
     */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> handleGeneric(Exception ex) {
        log.error("Unhandled exception while processing request", ex);
        ErrorResponse body = new ErrorResponse(
                HttpStatus.INTERNAL_SERVER_ERROR.value(),
                "Internal Server Error",
                "Something went wrong on our end.");
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(body);
    }
}
