package com.sms.dto;

import java.math.BigDecimal;

/**
 * Outbound representation of a {@code Student}.
 *
 * <p>This DTO deliberately omits the password (and any password hash) so the
 * stored credential is never exposed to the frontend (R9.2).</p>
 */
public class StudentResponseDTO {

    private Long id;
    private String name;
    private String course;
    private String mobile;
    private String email;
    private BigDecimal totalFees;
    private BigDecimal paidFees;
    private String username;

    public StudentResponseDTO() {
    }

    public StudentResponseDTO(Long id, String name, String course, String mobile, String email,
                             BigDecimal totalFees, BigDecimal paidFees, String username) {
        this.id = id;
        this.name = name;
        this.course = course;
        this.mobile = mobile;
        this.email = email;
        this.totalFees = totalFees;
        this.paidFees = paidFees;
        this.username = username;
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
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
}
