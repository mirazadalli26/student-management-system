package com.sms.security;

import java.io.IOException;
import java.util.List;

import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import com.sms.entity.Role;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

/**
 * Authenticates requests carrying a JWT bearer token.
 *
 * <p>On each request this filter reads the {@code Authorization} header and, if
 * it carries a {@code Bearer} token that {@link JwtUtils#validateToken(String)}
 * accepts, populates the {@link SecurityContextHolder} with a
 * {@link UsernamePasswordAuthenticationToken} whose authority is derived from
 * the token's role claim ({@code ROLE_ADMIN} / {@code ROLE_STUDENT}). Deriving
 * the authority from the claim avoids a per-request database lookup.</p>
 *
 * <p>A missing, malformed, or invalid/expired token leaves the security context
 * empty; the filter never throws and always continues the chain. Protected
 * routes are then rejected with HTTP 401 by the security entry point configured
 * in {@code SecurityConfig}. Satisfies Requirements 2.5, 2.6, 2.7, 3.5.</p>
 */
@Component
public class JwtAuthFilter extends OncePerRequestFilter {

    private static final String AUTHORIZATION_HEADER = "Authorization";
    private static final String BEARER_PREFIX = "Bearer ";

    private final JwtUtils jwtUtils;

    public JwtAuthFilter(JwtUtils jwtUtils) {
        this.jwtUtils = jwtUtils;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        String token = extractBearerToken(request);

        if (token != null
                && SecurityContextHolder.getContext().getAuthentication() == null
                && jwtUtils.validateToken(token)) {
            authenticate(request, token);
        }

        filterChain.doFilter(request, response);
    }

    /**
     * Returns the bearer token from the {@code Authorization} header, or
     * {@code null} when the header is absent or not a bearer token.
     */
    private String extractBearerToken(HttpServletRequest request) {
        String header = request.getHeader(AUTHORIZATION_HEADER);
        if (header != null && header.startsWith(BEARER_PREFIX)) {
            String token = header.substring(BEARER_PREFIX.length()).trim();
            return token.isEmpty() ? null : token;
        }
        return null;
    }

    /**
     * Builds and stores the authentication for a validated token. The username
     * comes from the token subject and the authority from the role claim; a
     * token without a recognizable role is treated as unauthenticated.
     */
    private void authenticate(HttpServletRequest request, String token) {
        String username = jwtUtils.getUsername(token);
        Role role = jwtUtils.getRole(token);
        if (username == null || role == null) {
            return;
        }

        List<SimpleGrantedAuthority> authorities = List.of(new SimpleGrantedAuthority("ROLE_" + role.name()));
        UsernamePasswordAuthenticationToken authentication =
                new UsernamePasswordAuthenticationToken(username, null, authorities);
        authentication.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));

        SecurityContextHolder.getContext().setAuthentication(authentication);
    }
}
