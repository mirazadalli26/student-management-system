package com.sms.config;

import java.io.IOException;
import java.time.Instant;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sms.exception.ErrorResponse;
import com.sms.security.JwtAuthFilter;

import jakarta.servlet.http.HttpServletResponse;

/**
 * Central Spring Security configuration for the stateless JWT REST API.
 *
 * <p>Uses the Spring Security 6 / Spring Boot 3 style: a {@link SecurityFilterChain}
 * bean built with the lambda DSL (no {@code WebSecurityConfigurerAdapter}). The
 * chain disables CSRF (there is no server-side session or cookie auth), forces a
 * {@link SessionCreationPolicy#STATELESS} policy, permits the static frontend and
 * the login endpoint, and applies role-based matchers over {@code /api/students/**}.</p>
 *
 * <p>The {@link JwtAuthFilter} runs before the
 * {@link UsernamePasswordAuthenticationFilter} to populate the security context
 * from a bearer token. Unauthenticated requests to protected routes are answered
 * with a JSON 401 via the {@link AuthenticationEntryPoint}; authenticated but
 * unauthorized requests get a JSON 403 via the {@link AccessDeniedHandler}. Both
 * reuse the {@link ErrorResponse} envelope so the frontend's error mapping stays
 * uniform, and neither exposes stack traces.</p>
 *
 * <p>Satisfies Requirements 3.1, 3.2, 3.5, 3.6.</p>
 */
@Configuration
@EnableWebSecurity
@EnableMethodSecurity
public class SecurityConfig {

    private static final String ROLE_ADMIN = "ADMIN";
    private static final String ROLE_STUDENT = "STUDENT";

    private final JwtAuthFilter jwtAuthFilter;
    private final ObjectMapper objectMapper;

    public SecurityConfig(JwtAuthFilter jwtAuthFilter, ObjectMapper objectMapper) {
        this.jwtAuthFilter = jwtAuthFilter;
        this.objectMapper = objectMapper;
    }

    /**
     * BCrypt encoder used to hash and verify account passwords (R1.3, R5.10).
     */
    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    /**
     * Exposes the container-managed {@link AuthenticationManager} so the
     * {@code AuthController} can authenticate login credentials. It resolves
     * principals through {@code CustomUserDetailsService} and verifies passwords
     * with the {@link #passwordEncoder()} bean via Spring's default
     * {@code DaoAuthenticationProvider}.
     */
    @Bean
    public AuthenticationManager authenticationManager(AuthenticationConfiguration configuration)
            throws Exception {
        return configuration.getAuthenticationManager();
    }

    /**
     * Builds the stateless JWT security filter chain.
     */
    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
            .csrf(csrf -> csrf.disable())
            .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .authorizeHttpRequests(auth -> auth
                // Public static frontend assets and the SPA entry points.
                .requestMatchers(
                    "/",
                    "/index.html",
                    "/login.html",
                    "/dashboard.html",
                    "/app.js",
                    "/favicon.ico",
                    "/*.html",
                    "/*.js",
                    "/*.css",
                    "/css/**",
                    "/js/**",
                    "/assets/**",
                    "/static/**",
                    "/webjars/**").permitAll()
                // Public authentication endpoint.
                .requestMatchers(HttpMethod.POST, "/api/auth/login").permitAll()
                // Current student's own record: STUDENT (and ADMIN) may read it.
                .requestMatchers(HttpMethod.GET, "/api/students/me").hasAnyRole(ROLE_ADMIN, ROLE_STUDENT)
                // Admin-only collection operations.
                .requestMatchers(HttpMethod.GET, "/api/students").hasRole(ROLE_ADMIN)
                .requestMatchers(HttpMethod.POST, "/api/students").hasRole(ROLE_ADMIN)
                .requestMatchers(HttpMethod.PUT, "/api/students/**").hasRole(ROLE_ADMIN)
                .requestMatchers(HttpMethod.DELETE, "/api/students/**").hasRole(ROLE_ADMIN)
                // Single-record read: both roles reach the controller, which
                // enforces the student-owns-record rule and returns 403 otherwise.
                .requestMatchers(HttpMethod.GET, "/api/students/*").hasAnyRole(ROLE_ADMIN, ROLE_STUDENT)
                // Everything else requires authentication.
                .anyRequest().authenticated())
            .exceptionHandling(exceptions -> exceptions
                .authenticationEntryPoint(authenticationEntryPoint())
                .accessDeniedHandler(accessDeniedHandler()))
            .addFilterBefore(jwtAuthFilter, UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }

    /**
     * JSON 401 entry point for missing/invalid/expired credentials (R3.5).
     */
    private AuthenticationEntryPoint authenticationEntryPoint() {
        return (request, response, authException) ->
            writeError(response, HttpServletResponse.SC_UNAUTHORIZED, "Unauthorized",
                "Authentication is required to access this resource.");
    }

    /**
     * JSON 403 handler for authenticated-but-forbidden requests (R3.2, R3.6).
     */
    private AccessDeniedHandler accessDeniedHandler() {
        return (request, response, accessDeniedException) ->
            writeError(response, HttpServletResponse.SC_FORBIDDEN, "Forbidden",
                "You do not have permission to access this resource.");
    }

    /**
     * Writes an {@link ErrorResponse} envelope as JSON. No exception detail or
     * stack trace is exposed to the client (R10.3).
     */
    private void writeError(HttpServletResponse response, int status, String error, String message)
            throws IOException {
        response.setStatus(status);
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding("UTF-8");
        ErrorResponse body = new ErrorResponse(Instant.now(), status, error, message, null);
        objectMapper.writeValue(response.getWriter(), body);
    }
}
