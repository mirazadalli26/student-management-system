package com.sms.controller;

import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.sms.dto.StudentRequestDTO;
import com.sms.dto.StudentResponseDTO;
import com.sms.dto.ValidationGroups.Create;
import com.sms.service.StudentService;

import jakarta.validation.Valid;
import jakarta.validation.groups.Default;

/**
 * REST endpoints for managing {@code Student} records.
 *
 * <p>Coarse-grained role authorization is applied by {@code SecurityConfig}'s
 * URL matchers: create, list-all, update, and delete require {@code ROLE_ADMIN}
 * (a STUDENT bearer token is rejected with HTTP 403, R3.2); the single-record
 * read ({@code GET /{id}}) and {@code GET /me} are reachable by both ADMIN and
 * STUDENT. This controller adds the fine-grained ownership rule for
 * {@code GET /{id}}: a STUDENT may read only its own record, otherwise the
 * request is denied with HTTP 403 (R3.3, R3.4, R9.5).</p>
 *
 * <p>Validation failures (400), username conflicts (409), and not-found
 * conditions (404) are translated to the standard JSON envelope by the
 * {@code GlobalExceptionHandler}; missing/invalid tokens (401) and forbidden
 * roles (403) are handled by {@code SecurityConfig}'s JSON handlers.</p>
 *
 * <p>Satisfies Requirements 3.1, 3.2, 3.3, 3.4, 5.2, 6.1, 7.2, 8.3, 9.1, 9.6.</p>
 */
@RestController
@RequestMapping("/api/students")
public class StudentController {

    private static final String ROLE_ADMIN = "ROLE_ADMIN";
    private static final String ROLE_STUDENT = "ROLE_STUDENT";

    private final StudentService studentService;

    public StudentController(StudentService studentService) {
        this.studentService = studentService;
    }

    /**
     * Creates a new Student_Record. ADMIN-only via the security matcher.
     *
     * <p>The request is validated against both the default constraints and the
     * {@link Create} group so that the password is required on create (R5.9)
     * while the field-level format/length/range rules also run.</p>
     *
     * @param request the create payload
     * @return HTTP 201 with the created student (never containing the password) (R5.2)
     */
    @PostMapping
    public ResponseEntity<StudentResponseDTO> create(
            @Validated({Default.class, Create.class}) @RequestBody StudentRequestDTO request) {
        StudentResponseDTO created = studentService.create(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(created);
    }

    /**
     * Lists all Student_Records ordered alphabetically by name. ADMIN-only via
     * the security matcher.
     *
     * @return HTTP 200 with the ordered list (R6.1)
     */
    @GetMapping
    public ResponseEntity<List<StudentResponseDTO>> findAll() {
        return ResponseEntity.ok(studentService.findAllOrderedByName());
    }

    /**
     * Reads a single Student_Record by identifier.
     *
     * <p>ADMIN may read any record. A STUDENT (a principal holding
     * {@code ROLE_STUDENT} but not {@code ROLE_ADMIN}) may read only its own
     * record: the target record's username must equal the authenticated
     * principal name, otherwise the request is denied with HTTP 403
     * (R3.3, R3.4, R9.5).</p>
     *
     * @param id             the student identifier
     * @param authentication the current authentication (principal + authorities)
     * @return HTTP 200 with the record when authorized
     * @throws AccessDeniedException when a STUDENT requests a record that is not its own
     */
    @GetMapping("/{id}")
    public ResponseEntity<StudentResponseDTO> findById(@PathVariable Long id,
                                                       Authentication authentication) {
        StudentResponseDTO student = studentService.findById(id);
        if (isStudentOnly(authentication) && !student.getUsername().equals(authentication.getName())) {
            throw new AccessDeniedException("You are not authorized to access this record.");
        }
        return ResponseEntity.ok(student);
    }

    /**
     * Updates an existing Student_Record. ADMIN-only via the security matcher.
     *
     * <p>Validated against the default group only, so the password is optional
     * on update and the existing hash is retained when none is supplied (R7.8).</p>
     *
     * @param id      the student identifier
     * @param request the update payload
     * @return HTTP 200 with the updated student (R7.2)
     */
    @PutMapping("/{id}")
    public ResponseEntity<StudentResponseDTO> update(@PathVariable Long id,
                                                     @Valid @RequestBody StudentRequestDTO request) {
        return ResponseEntity.ok(studentService.update(id, request));
    }

    /**
     * Permanently removes a Student_Record. ADMIN-only via the security matcher.
     *
     * @param id the student identifier
     * @return HTTP 200 on success (R8.3)
     */
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        studentService.delete(id);
        return ResponseEntity.ok().build();
    }

    /**
     * Returns the currently authenticated student's own Student_Record,
     * resolved by the JWT subject (username).
     *
     * @param authentication the current authentication whose name is the username
     * @return HTTP 200 with the caller's own record (R9.1); a missing record
     *         yields HTTP 404 via {@code ResourceNotFoundException} (R9.6)
     */
    @GetMapping("/me")
    public ResponseEntity<StudentResponseDTO> me(Authentication authentication) {
        return ResponseEntity.ok(studentService.findByUsername(authentication.getName()));
    }

    /**
     * Determines whether the caller is a plain STUDENT: it holds
     * {@code ROLE_STUDENT} and does not hold {@code ROLE_ADMIN}. ADMINs are
     * exempt from the ownership restriction.
     *
     * @param authentication the current authentication
     * @return {@code true} if the caller is a student without admin privileges
     */
    private boolean isStudentOnly(Authentication authentication) {
        boolean isStudent = false;
        boolean isAdmin = false;
        for (GrantedAuthority authority : authentication.getAuthorities()) {
            String name = authority.getAuthority();
            if (ROLE_ADMIN.equals(name)) {
                isAdmin = true;
            } else if (ROLE_STUDENT.equals(name)) {
                isStudent = true;
            }
        }
        return isStudent && !isAdmin;
    }
}
