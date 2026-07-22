package com.sms.exception;

/**
 * Thrown when a requested Student_Record (or other resource) does not exist.
 * Mapped to HTTP 404 (Not Found) by the {@link GlobalExceptionHandler}.
 *
 * <p>Satisfies R7.4, R8.4, R9.6.</p>
 */
public class ResourceNotFoundException extends RuntimeException {

    public ResourceNotFoundException(String message) {
        super(message);
    }

    public ResourceNotFoundException(String message, Throwable cause) {
        super(message, cause);
    }
}
