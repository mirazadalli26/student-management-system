package com.sms.dto;

/**
 * Successful login response carrying the issued JWT along with the
 * authenticated account's role and username (R2.1).
 */
public class JwtResponse {

    private String token;
    private String role;
    private String username;

    public JwtResponse() {
    }

    public JwtResponse(String token, String role, String username) {
        this.token = token;
        this.role = role;
        this.username = username;
    }

    public String getToken() {
        return token;
    }

    public void setToken(String token) {
        this.token = token;
    }

    public String getRole() {
        return role;
    }

    public void setRole(String role) {
        this.role = role;
    }

    public String getUsername() {
        return username;
    }

    public void setUsername(String username) {
        this.username = username;
    }
}
