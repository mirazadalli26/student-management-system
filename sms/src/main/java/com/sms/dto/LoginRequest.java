package com.sms.dto;

import jakarta.validation.constraints.NotBlank;

/**
 * Inbound payload for {@code POST /api/auth/login}.
 *
 * <p>A missing or blank username or password is a validation error that must be
 * rejected with HTTP 400 before any credential check occurs (R2.3).</p>
 */
public class LoginRequest {

    @NotBlank(message = "username is required")
    private String username;

    @NotBlank(message = "password is required")
    private String password;

    public LoginRequest() {
    }

    public LoginRequest(String username, String password) {
        this.username = username;
        this.password = password;
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
