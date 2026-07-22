package com.sms.security;

import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.stereotype.Component;

/**
 * In-memory tracker of consecutive failed login attempts per account, used to
 * enforce the lockout policy of Requirement 2.8.
 *
 * <p>Policy: 5 consecutive failed login attempts for the same account within a
 * 15-minute window lock that account for 15 minutes. While locked, further login
 * attempts are rejected (the controller returns HTTP 401). A successful login
 * resets the account's failure state.</p>
 *
 * <p>State is held in a {@link ConcurrentHashMap} keyed by username; each entry
 * is mutated under its own monitor so updates are thread-safe. This is a
 * process-local tracker: it intentionally trades cross-instance accuracy for
 * simplicity, matching the single-process default deployment.</p>
 */
@Component
public class LoginAttemptService {

    /** Number of consecutive failures within the window that triggers a lock. */
    static final int MAX_FAILED_ATTEMPTS = 5;

    /** Sliding window in which consecutive failures are counted. */
    static final Duration ATTEMPT_WINDOW = Duration.ofMinutes(15);

    /** Duration an account remains locked once the threshold is reached. */
    static final Duration LOCK_DURATION = Duration.ofMinutes(15);

    private final ConcurrentHashMap<String, Attempt> attempts = new ConcurrentHashMap<>();

    /**
     * Reports whether the account is currently locked out.
     *
     * @param username the account username
     * @return {@code true} if the account is locked and the lock has not yet expired
     */
    public boolean isLocked(String username) {
        if (username == null) {
            return false;
        }
        Attempt attempt = attempts.get(username);
        if (attempt == null) {
            return false;
        }
        synchronized (attempt) {
            Instant lockedUntil = attempt.lockedUntil;
            if (lockedUntil == null) {
                return false;
            }
            if (Instant.now().isBefore(lockedUntil)) {
                return true;
            }
            // Lock has expired; clear the state so the next failure starts fresh.
            attempts.remove(username, attempt);
            return false;
        }
    }

    /**
     * Records a failed login attempt for the account, applying the sliding
     * window and locking the account once the threshold is reached.
     *
     * @param username the account username
     */
    public void recordFailure(String username) {
        if (username == null) {
            return;
        }
        Instant now = Instant.now();
        attempts.compute(username, (key, existing) -> {
            Attempt attempt = existing == null ? new Attempt() : existing;
            synchronized (attempt) {
                // Reset the window if it has elapsed since the first counted failure.
                if (attempt.windowStart == null
                        || Duration.between(attempt.windowStart, now).compareTo(ATTEMPT_WINDOW) > 0) {
                    attempt.windowStart = now;
                    attempt.failures = 0;
                }
                attempt.failures++;
                if (attempt.failures >= MAX_FAILED_ATTEMPTS) {
                    attempt.lockedUntil = now.plus(LOCK_DURATION);
                }
            }
            return attempt;
        });
    }

    /**
     * Clears all failure state for the account after a successful login.
     *
     * @param username the account username
     */
    public void recordSuccess(String username) {
        if (username != null) {
            attempts.remove(username);
        }
    }

    /**
     * Mutable per-account failure state. Guarded by its own monitor.
     */
    private static final class Attempt {
        private int failures;
        private Instant windowStart;
        private Instant lockedUntil;
    }
}
