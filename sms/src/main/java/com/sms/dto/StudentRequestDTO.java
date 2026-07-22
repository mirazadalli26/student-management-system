package com.sms.dto;

import com.sms.dto.ValidationGroups.Create;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;

/**
 * Inbound payload for creating and updating a {@code Student}.
 *
 * <p>Field-level Bean Validation enforces the required/format/length/range
 * rules from the design's validation summary (R5.3, R5.4, R5.7, R5.8, R5.9).
 * The {@code password} is required only on create via the {@link Create} group;
 * on update it may be omitted to retain the existing hash (R7.8). Cross-field
 * checks (paid &le; total, username uniqueness) are enforced in the service
 * layer.</p>
 */
public class StudentRequestDTO {

    @NotBlank(message = "name is required")
    @Size(max = 100, message = "name must be at most 100 characters")
    private String name;

    @NotBlank(message = "course is required")
    private String course;

    @NotBlank(message = "mobile is required")
    @Pattern(regexp = "\\d{10}", message = "mobile must be exactly 10 digits")
    private String mobile;

    @NotBlank(message = "email is required")
    @Email(message = "must be a valid email address")
    private String email;

    @NotNull(message = "totalFees is required")
    @DecimalMin(value = "0.00", message = "totalFees must be at least 0.00")
    @DecimalMax(value = "9999999.99", message = "totalFees must be at most 9999999.99")
    @Digits(integer = 8, fraction = 2, message = "totalFees must have at most 8 integer and 2 fractional digits")
    private BigDecimal totalFees;

    @NotNull(message = "paidFees is required")
    @DecimalMin(value = "0.00", message = "paidFees must be at least 0.00")
    @DecimalMax(value = "9999999.99", message = "paidFees must be at most 9999999.99")
    @Digits(integer = 8, fraction = 2, message = "paidFees must have at most 8 integer and 2 fractional digits")
    private BigDecimal paidFees;

    @NotBlank(message = "username is required")
    @Size(min = 4, max = 30, message = "username must be between 4 and 30 characters")
    private String username;

    // Required on create only; when supplied (create or update) it must be 8-64 chars.
    @NotBlank(groups = Create.class, message = "password is required")
    @Size(min = 8, max = 64, message = "password must be between 8 and 64 characters")
    private String password;

    public StudentRequestDTO() {
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getCourse() {
        return course;
    }

    public void setCourse(String course) {
        this.course = course;
    }

    public String getMobile() {
        return mobile;
    }

    public void setMobile(String mobile) {
        this.mobile = mobile;
    }

    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = email;
    }

    public BigDecimal getTotalFees() {
        return totalFees;
    }

    public void setTotalFees(BigDecimal totalFees) {
        this.totalFees = totalFees;
    }

    public BigDecimal getPaidFees() {
        return paidFees;
    }

    public void setPaidFees(BigDecimal paidFees) {
        this.paidFees = paidFees;
    }

    public String getUsername() {
        return username;
    }

    public void setUsername(String username) {
        this.username = username;
    }

    public String getPassword() {
        return password;
    }

    public void setPassword(String password) {
        this.password = password;
    }
}
