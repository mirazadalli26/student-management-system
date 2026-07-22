package com.sms.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.sms.entity.Student;

/**
 * Spring Data JPA repository for {@link Student} records.
 *
 * <p>Supports login lookups, username uniqueness checks (R5.5), and
 * alphabetically ordered listing (R6.1).
 */
@Repository
public interface StudentRepository extends JpaRepository<Student, Long> {

    /**
     * Finds a student by username for authentication and self-service lookups.
     *
     * @param username the account username
     * @return the matching student, if present
     */
    Optional<Student> findByUsername(String username);

    /**
     * Determines whether a student with the given username exists. Used together
     * with the admin repository to enforce username uniqueness across the
     * accounts namespace (R5.5).
     *
     * @param username the username to check
     * @return {@code true} if a student with the username exists
     */
    boolean existsByUsername(String username);

    /**
     * Returns all students ordered by name ascending (R6.1).
     *
     * @return the ordered list of students
     */
    List<Student> findAllByOrderByNameAsc();
}
