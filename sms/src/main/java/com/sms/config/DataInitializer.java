package com.sms.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

import com.sms.entity.Admin;
import com.sms.entity.Role;
import com.sms.repository.AdminRepository;

/**
 * Provisions the single default {@link Admin} account at application startup so
 * the system is manageable immediately after deployment without manual database
 * setup.
 *
 * <p>On startup this runner:</p>
 * <ul>
 *   <li>Creates exactly one Administrator from configuration
 *       ({@code sms.admin.username} / {@code sms.admin.password}) only when no
 *       account with the {@link Role#ADMIN} role already exists (R1.1).</li>
 *   <li>Makes no change when an admin already exists (R1.2).</li>
 *   <li>Stores the password as a one-way BCrypt hash, never in plain text
 *       (R1.3), and assigns the {@code ADMIN} role (R1.4).</li>
 *   <li>Aborts atomically on failure. The account is materialized with a hashed
 *       password before the single {@code save} call, so a failure leaves no
 *       partial or unhashed account; the error is logged and rethrown to signal
 *       that provisioning failed (R1.5).</li>
 * </ul>
 *
 * <p>The raw password is never written to the logs.</p>
 *
 * <p>Satisfies Requirements 1.1, 1.2, 1.3, 1.4, 1.5.</p>
 */
@Component
public class DataInitializer implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(DataInitializer.class);

    private final AdminRepository adminRepository;
    private final PasswordEncoder passwordEncoder;
    private final String adminUsername;
    private final String adminPassword;

    public DataInitializer(
            AdminRepository adminRepository,
            PasswordEncoder passwordEncoder,
            @Value("${sms.admin.username}") String adminUsername,
            @Value("${sms.admin.password}") String adminPassword) {
        this.adminRepository = adminRepository;
        this.passwordEncoder = passwordEncoder;
        this.adminUsername = adminUsername;
        this.adminPassword = adminPassword;
    }

    @Override
    public void run(ApplicationArguments args) {
        // R1.2: if any ADMIN already exists, leave everything unchanged.
        if (adminRepository.existsByRole(Role.ADMIN)) {
            log.info("Default administrator provisioning skipped: an account with the ADMIN role already exists.");
            return;
        }

        try {
            // R1.3 + R1.4: hash the password and assign the ADMIN role before
            // persisting. The single save is the only write, so any failure
            // leaves no partial or unhashed account (R1.5).
            Admin admin = new Admin(adminUsername, passwordEncoder.encode(adminPassword), Role.ADMIN);
            adminRepository.save(admin);
            log.info("Default administrator account provisioned for username '{}'.", adminUsername);
        } catch (RuntimeException ex) {
            // R1.5: signal that provisioning failed; never log the raw password.
            log.error("Default administrator provisioning failed for username '{}': {}",
                    adminUsername, ex.getMessage());
            throw ex;
        }
    }
}
