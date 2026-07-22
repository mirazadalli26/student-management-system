package com.sms.exception;

/**
 * Thrown when a create/update request attempts to use a username that already
 * belongs to an existing account. Mapped to HTTP 409 (Conflict) by the
 * {@link GlobalExceptionHandler}.
 *
 * <p>Satisfies R5.5.</p>
 */
public class UsernameConflictException extends RuntimeException {

    public UsernameConflictException(String message) {
        super(message);
    }

    public UsernameConflictException(String message, Throwable cause) {
        super(message, cause);
    }
}
