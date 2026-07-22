package com.sms.exception;

import java.time.Instant;
import java.util.Map;

/**
 * Consistent JSON error envelope returned by the {@code GlobalExceptionHandler}.
 *
 * <p>Only safe, user-facing information is included; raw exceptions, stack
 * traces, and SQL details are never exposed (R10.3). {@code fieldErrors} maps a
 * field name to a human-readable validation message and is present only when
 * field-level validation failures apply.</p>
 */
public class ErrorResponse {

    private Instant timestamp;
    private int status;
    private String error;
    private String message;
    private Map<String, String> fieldErrors;

    public ErrorResponse() {
    }

    public ErrorResponse(Instant timestamp, int status, String error, String message,
                         Map<String, String> fieldErrors) {
        this.timestamp = timestamp;
        this.status = status;
        this.error = error;
        this.message = message;
        this.fieldErrors = fieldErrors;
    }

    public ErrorResponse(int status, String error, String message) {
        this(Instant.now(), status, error, message, null);
    }

    public Instant getTimestamp() {
        return timestamp;
    }

    public void setTimestamp(Instant timestamp) {
        this.timestamp = timestamp;
    }

    public int getStatus() {
        return status;
    }

    public void setStatus(int status) {
        this.status = status;
    }

    public String getError() {
        return error;
    }

    public void setError(String error) {
        this.error = error;
    }

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }

    public Map<String, String> getFieldErrors() {
        return fieldErrors;
    }

    public void setFieldErrors(Map<String, String> fieldErrors) {
        this.fieldErrors = fieldErrors;
    }
}
