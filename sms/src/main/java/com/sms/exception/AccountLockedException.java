package com.sms.exception;

/**
 * Raised when a login attempt is made against an account that is currently
 * locked out because of too many consecutive failed attempts (R2.8).
 *
 * <p>Handled by the {@code GlobalExceptionHandler}, which maps it to HTTP 401
 * with a safe, user-facing message.</p>
 */
public class AccountLockedException extends RuntimeException {

    public AccountLockedException(String message) {
        super(message);
    }
}
