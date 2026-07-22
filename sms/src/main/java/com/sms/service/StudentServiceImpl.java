package com.sms.service;

import java.util.List;
import java.util.stream.Collectors;

import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.sms.dto.StudentRequestDTO;
import com.sms.dto.StudentResponseDTO;
import com.sms.entity.Role;
import com.sms.entity.Student;
import com.sms.exception.ResourceNotFoundException;
import com.sms.exception.UsernameConflictException;
import com.sms.repository.AdminRepository;
import com.sms.repository.StudentRepository;

/**
 * Default {@link StudentService} implementation.
 *
 * <p>Holds the cross-field business rules (paid &le; total), cross-repository
 * username uniqueness enforcement (R5.5 &rarr; 409), password hashing and
 * STUDENT-role assignment on create (R5.10), and DTO mapping that never exposes
 * the stored password hash (R9.2). Field-level validation (required/format/
 * length/range) is enforced by Bean Validation on {@link StudentRequestDTO} at
 * the controller boundary.</p>
 */
@Service
public class StudentServiceImpl implements StudentService {

    private final StudentRepository studentRepository;
    private final AdminRepository adminRepository;
    private final PasswordEncoder passwordEncoder;

    public StudentServiceImpl(StudentRepository studentRepository,
                              AdminRepository adminRepository,
                              PasswordEncoder passwordEncoder) {
        this.studentRepository = studentRepository;
        this.adminRepository = adminRepository;
        this.passwordEncoder = passwordEncoder;
    }

    /**
     * {@inheritDoc}
     *
     * <p>Enforces the paid &le; total cross-field rule (R5.6), username
     * uniqueness across both the student and admin repositories (R5.5), hashes
     * the password (R5.10), and assigns the STUDENT role (R5.10).</p>
     */
    @Override
    @Transactional
    public StudentResponseDTO create(StudentRequestDTO request) {
        // Cross-field business rule: paid fees may not exceed total fees (R5.6).
        if (request.getPaidFees().compareTo(request.getTotalFees()) > 0) {
            throw new IllegalArgumentException("paidFees cannot exceed totalFees");
        }

        // Username uniqueness across the entire accounts namespace (R5.5 -> 409).
        String username = request.getUsername();
        if (studentRepository.existsByUsername(username) || adminRepository.existsByUsername(username)) {
            throw new UsernameConflictException("username already exists: " + username);
        }

        Student student = new Student();
        student.setName(request.getName());
        student.setCourse(request.getCourse());
        student.setMobile(request.getMobile());
        student.setEmail(request.getEmail());
        student.setTotalFees(request.getTotalFees());
        student.setPaidFees(request.getPaidFees());
        student.setUsername(username);
        // Store a one-way BCrypt hash, never the plaintext (R5.10).
        student.setPassword(passwordEncoder.encode(request.getPassword()));
        // Every created account carries the STUDENT role (R5.10).
        student.setRole(Role.STUDENT);

        Student saved = studentRepository.save(student);
        return toResponseDTO(saved);
    }

    /**
     * {@inheritDoc}
     *
     * <p>Delegates ordering to the repository query so results are sorted by
     * name ascending (R6.1).</p>
     */
    @Override
    @Transactional(readOnly = true)
    public List<StudentResponseDTO> findAllOrderedByName() {
        return studentRepository.findAllByOrderByNameAsc().stream()
                .map(this::toResponseDTO)
                .collect(Collectors.toList());
    }

    /**
     * {@inheritDoc}
     *
     * @throws ResourceNotFoundException if no student has the given id (404)
     */
    @Override
    @Transactional(readOnly = true)
    public StudentResponseDTO findById(Long id) {
        Student student = studentRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("student not found with id: " + id));
        return toResponseDTO(student);
    }

    /**
     * {@inheritDoc}
     *
     * <p>Used by the {@code /me} self-service view (R9.1).</p>
     *
     * @throws ResourceNotFoundException if no student has the given username (R9.6)
     */
    @Override
    @Transactional(readOnly = true)
    public StudentResponseDTO findByUsername(String username) {
        Student student = studentRepository.findByUsername(username)
                .orElseThrow(() -> new ResourceNotFoundException("student not found with username: " + username));
        return toResponseDTO(student);
    }

    /**
     * {@inheritDoc}
     *
     * <p>Loads the target record (404 when absent, R7.4), enforces the
     * paid &le; total cross-field rule (R7.7 &rarr; 400), and enforces username
     * uniqueness across both repositories only when the username actually
     * changes (R5.5 &rarr; 409). Field-level validation
     * (required/email/mobile/length/range) is enforced by Bean Validation on the
     * DTO at the controller boundary (R7.5, R7.6). When no new password is
     * supplied, the existing stored hash is retained (R7.8); otherwise the new
     * password is hashed. The returned DTO never carries the password (R9.2).</p>
     *
     * @throws ResourceNotFoundException if no student has the given id (R7.4 -> 404)
     * @throws IllegalArgumentException  if paid fees exceed total fees (R7.7 -> 400)
     * @throws UsernameConflictException if the new username is already taken (R5.5 -> 409)
     */
    @Override
    @Transactional
    public StudentResponseDTO update(Long id, StudentRequestDTO request) {
        // Load the target record; absence is a 404 with no mutation (R7.4).
        Student student = studentRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("student not found with id: " + id));

        // Cross-field business rule: paid fees may not exceed total fees (R7.7).
        if (request.getPaidFees().compareTo(request.getTotalFees()) > 0) {
            throw new IllegalArgumentException("paidFees cannot exceed totalFees");
        }

        // Username uniqueness is only re-checked when the username actually
        // changes; an unchanged username must be allowed (R5.5 -> 409).
        String username = request.getUsername();
        if (!username.equals(student.getUsername())
                && (studentRepository.existsByUsername(username) || adminRepository.existsByUsername(username))) {
            throw new UsernameConflictException("username already exists: " + username);
        }

        student.setName(request.getName());
        student.setCourse(request.getCourse());
        student.setMobile(request.getMobile());
        student.setEmail(request.getEmail());
        student.setTotalFees(request.getTotalFees());
        student.setPaidFees(request.getPaidFees());
        student.setUsername(username);

        // Password retention: keep the existing hash when no new password is
        // supplied, otherwise store a fresh BCrypt hash (R7.8).
        String password = request.getPassword();
        if (password != null && !password.isBlank()) {
            student.setPassword(passwordEncoder.encode(password));
        }

        Student saved = studentRepository.save(student);
        return toResponseDTO(saved);
    }

    /**
     * {@inheritDoc}
     *
     * <p>Permanently removes the record (R8.3). Absence is a 404 with no change
     * to any other record (R8.4). Because the record and its credentials are
     * gone, {@code CustomUserDetailsService} can no longer resolve them, so the
     * former credentials can no longer authenticate (R8.6).</p>
     *
     * @throws ResourceNotFoundException if no student has the given id (R8.4 -> 404)
     */
    @Override
    @Transactional
    public void delete(Long id) {
        if (!studentRepository.existsById(id)) {
            throw new ResourceNotFoundException("student not found with id: " + id);
        }
        studentRepository.deleteById(id);
    }

    /**
     * Maps a {@link Student} entity to a {@link StudentResponseDTO}, deliberately
     * omitting the password hash so it is never exposed to the frontend (R9.2).
     *
     * @param student the entity to map
     * @return the response DTO
     */
    private StudentResponseDTO toResponseDTO(Student student) {
        return new StudentResponseDTO(
                student.getId(),
                student.getName(),
                student.getCourse(),
                student.getMobile(),
                student.getEmail(),
                student.getTotalFees(),
                student.getPaidFees(),
                student.getUsername());
    }
}
