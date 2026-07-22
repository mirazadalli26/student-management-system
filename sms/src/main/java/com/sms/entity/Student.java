package com.sms.entity;

import java.math.BigDecimal;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * Student record containing academic, contact, and financial information along
 * with login credentials. The password is always stored as a BCrypt hash, and
 * every Student account carries the STUDENT role.
 */
@Entity
@Table(name = "student")
public class Student {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 100)
    private String name;

    @Column(nullable = false)
    private String course;

    @Column(nullable = false, length = 10)
    private String mobile;

    @Column(nullable = false)
    private String email;

    @Column(name = "total_fees", nullable = false, precision = 10, scale = 2)
    private BigDecimal totalFees;

    @Column(name = "paid_fees", nullable = false, precision = 10, scale = 2)
    private BigDecimal paidFees;

    @Column(nullable = false, unique = true, length = 30)
    private String username;

    @Column(nullable = false)
    private String password;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private Role role;

    public Student() {
    }

    public Student(String name, String course, String mobile, String email,
                   BigDecimal totalFees, BigDecimal paidFees,
                   String username, String password, Role role) {
        this.name = name;
        this.course = course;
        this.mobile = mobile;
        this.email = email;
        this.totalFees = totalFees;
        this.paidFees = paidFees;
        this.username = username;
        this.password = password;
        this.role = role;
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

    public String getPassword() {
        return password;
    }

    public void setPassword(String password) {
        this.password = password;
    }

    public Role getRole() {
        return role;
    }

    public void setRole(Role role) {
        this.role = role;
    }
}
