package com.sms.dto;

/**
 * Bean Validation group markers used to vary constraints between operations.
 *
 * <p>The {@link Create} group activates constraints that only apply when a new
 * {@code Student} is being created (for example, a required password). Update
 * requests validate against the default group only, so those create-only
 * constraints are skipped and the password becomes optional (R7.8).</p>
 */
public final class ValidationGroups {

    private ValidationGroups() {
    }

    /** Constraints that apply only when creating a new record. */
    public interface Create {
    }
}
