package com.sms.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.sms.dto.JwtResponse;
import com.sms.dto.LoginRequest;
import com.sms.entity.Role;
import com.sms.exception.AccountLockedException;
import com.sms.security.JwtUtils;
import com.sms.security.LoginAttemptService;

import jakarta.validation.Valid;

/**
 * Authentication endpoint for the shared login flow.
 *
 * <p>{@code POST /api/auth/login} validates that a username and password are
 * present (missing values are rejected with HTTP 400 by Bean Validation before
 * this handler runs, R2.3), enforces the per-account lockout policy (R2.8),
 * verifies the credentials through the container-managed
 * {@link AuthenticationManager} (BCrypt via {@code CustomUserDetailsService}),
 * and, on success, issues a 15-minute JWT carrying the account identity and
 * role (R2.1, R2.4).</p>
 *
 * <p>Failure paths return HTTP 401 with the standard error envelope:
 * bad/unknown credentials via {@link AuthenticationException} and a locked
 * account via {@link AccountLockedException}, both mapped by the
 * {@code GlobalExceptionHandler} (R2.2, R2.8).</p>
 */
@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final AuthenticationManager authenticationManager;
    private final JwtUtils jwtUtils;
    private final LoginAttemptService loginAttemptService;

    public AuthController(AuthenticationManager authenticationManager,
                          JwtUtils jwtUtils,
                          LoginAttemptService loginAttemptService) {
        this.authenticationManager = authenticationManager;
        this.jwtUtils = jwtUtils;
        this.loginAttemptService = loginAttemptService;
    }

    /**
     * Authenticates a user and issues a JWT on success.
     *
     * @param loginRequest the validated username/password payload
     * @return HTTP 200 with a {@link JwtResponse} carrying the token, role, and username
     * @throws AccountLockedException  if the account is currently locked out (R2.8, mapped to 401)
     * @throws AuthenticationException if the credentials do not match (R2.2, mapped to 401)
     */
    @PostMapping("/login")
    public ResponseEntity<JwtResponse> login(@Valid @RequestBody LoginRequest loginRequest) {
        String username = loginRequest.getUsername();

        // R2.8: reject while the account is locked, before touching credentials.
        if (loginAttemptService.isLocked(username)) {
            throw new AccountLockedException(
                    "Account is temporarily locked due to too many failed login attempts. Please try again later.");
        }

        Authentication authentication;
        try {
            authentication = authenticationManager.authenticate(
                    new UsernamePasswordAuthenticationToken(username, loginRequest.getPassword()));
        } catch (AuthenticationException ex) {
            // R2.2: wrong/unknown credentials -> record the failure and return 401.
            loginAttemptService.recordFailure(username);
            throw ex;
        }

        // R2.1: successful login clears the failure state and issues a token.
        loginAttemptService.recordSuccess(username);

        Role role = resolveRole(authentication);
        String token = jwtUtils.generateToken(authentication.getName(), role);

        JwtResponse body = new JwtResponse(token, role != null ? role.name() : null, authentication.getName());
        return ResponseEntity.ok(body);
    }

    /**
     * Derives the account {@link Role} from the authenticated principal's
     * granted authority ({@code ROLE_ADMIN -> ADMIN}, {@code ROLE_STUDENT ->
     * STUDENT}).
     *
     * @param authentication the successful authentication
     * @return the resolved role, or {@code null} if no recognized role authority is present
     */
    private Role resolveRole(Authentication authentication) {
        for (GrantedAuthority authority : authentication.getAuthorities()) {
            String name = authority.getAuthority();
            if ("ROLE_ADMIN".equals(name)) {
                return Role.ADMIN;
            }
            if ("ROLE_STUDENT".equals(name)) {
                return Role.STUDENT;
            }
        }
        return null;
    }
}
