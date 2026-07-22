package com.sms.repository;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.sms.entity.Admin;
import com.sms.entity.Role;

/**
 * Spring Data JPA repository for {@link Admin} accounts.
 *
 * <p>Supports default-admin provisioning (R1.1, R1.2), login lookups, and
 * cross-repository username uniqueness checks (R5.5).
 */
@Repository
public interface AdminRepository extends JpaRepository<Admin, Long> {

    /**
     * Finds an administrator by username for authentication.
     *
     * @param username the account username
     * @return the matching admin, if present
     */
    Optional<Admin> findByUsername(String username);

    /**
     * Determines whether any account with the given role already exists. Used by
     * the {@code DataInitializer} to avoid creating a second default admin.
     *
     * @param role the role to check for
     * @return {@code true} if at least one admin has the given role
     */
    boolean existsByRole(Role role);

    /**
     * Determines whether an administrator with the given username exists. Used
     * together with the student repository to enforce username uniqueness across
     * the accounts namespace (R5.5).
     *
     * @param username the username to check
     * @return {@code true} if an admin with the username exists
     */
    boolean existsByUsername(String username);
}
