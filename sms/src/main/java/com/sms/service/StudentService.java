package com.sms.service;

import java.util.List;

import com.sms.dto.StudentRequestDTO;
import com.sms.dto.StudentResponseDTO;

/**
 * Business operations for managing {@code Student} records.
 *
 * <p>Implementations hold all validation, password-hashing, and transformation
 * rules. Responses never expose the stored password hash (R9.2).</p>
 */
public interface StudentService {

    /**
     * Creates a new Student_Record after enforcing cross-field validation and
     * username uniqueness, hashing the password, and assigning the STUDENT role.
     *
     * @param request the create payload
     * @return the created student as a response DTO (never containing the password)
     * @see "R5.2, R5.5, R5.6, R5.10"
     */
    StudentResponseDTO create(StudentRequestDTO request);

    /**
     * Returns all Student_Records ordered alphabetically by name ascending.
     *
     * @return the ordered list of students
     * @see "R6.1"
     */
    List<StudentResponseDTO> findAllOrderedByName();

    /**
     * Finds a Student_Record by its identifier.
     *
     * @param id the student identifier
     * @return the matching student as a response DTO
     * @see "R7, R9"
     */
    StudentResponseDTO findById(Long id);

    /**
     * Finds a Student_Record by username (used by the {@code /me} self-service view).
     *
     * @param username the account username
     * @return the matching student as a response DTO
     * @see "R9.1"
     */
    StudentResponseDTO findByUsername(String username);

    /**
     * Updates an existing Student_Record.
     *
     * @param id      the student identifier
     * @param request the update payload
     * @return the updated student as a response DTO
     * @see "R7"
     */
    StudentResponseDTO update(Long id, StudentRequestDTO request);

    /**
     * Permanently removes a Student_Record.
     *
     * @param id the student identifier
     * @see "R8"
     */
    void delete(Long id);
}
