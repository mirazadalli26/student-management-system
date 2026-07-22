package com.sms.security;

import java.time.Instant;
import java.util.Date;

import javax.crypto.SecretKey;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import com.sms.entity.Role;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.MalformedJwtException;
import io.jsonwebtoken.UnsupportedJwtException;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;
import io.jsonwebtoken.security.SignatureException;

/**
 * Utility for issuing and validating JSON Web Tokens (JWT).
 *
 * <p>Tokens carry the authenticated username as the subject and the account
 * {@link Role} as a {@code role} claim, are stamped with an issued-at time, and
 * expire exactly {@code expirationSeconds} (900s by default) after issuance.
 * Validation verifies both the HMAC signature and the expiry.</p>
 *
 * <p>Satisfies Requirements 2.4, 2.5, 2.6, 2.7.</p>
 */
@Component
public class JwtUtils {

    private static final Logger log = LoggerFactory.getLogger(JwtUtils.class);

    /** Claim name carrying the account role. */
    public static final String ROLE_CLAIM = "role";

    private final SecretKey signingKey;
    private final long expirationSeconds;

    public JwtUtils(
            @Value("${sms.jwt.secret}") String secret,
            @Value("${sms.jwt.expiration-seconds:900}") long expirationSeconds) {
        this.signingKey = buildSigningKey(secret);
        this.expirationSeconds = expirationSeconds;
    }

    /**
     * Builds an HMAC-SHA signing key from the configured secret. The secret is
     * expected to be Base64-encoded; if decoding does not yield enough key
     * material, the raw UTF-8 bytes are used as a fallback.
     */
    private static SecretKey buildSigningKey(String secret) {
        byte[] keyBytes;
        try {
            keyBytes = Decoders.BASE64.decode(secret);
        } catch (RuntimeException ex) {
            keyBytes = secret.getBytes(java.nio.charset.StandardCharsets.UTF_8);
        }
        return Keys.hmacShaKeyFor(keyBytes);
    }

    /**
     * Generates a signed JWT for the given user.
     *
     * @param username the account username, stored as the token subject
     * @param role     the account role, stored under the {@code role} claim
     * @return a compact, signed JWT string
     */
    public String generateToken(String username, Role role) {
        Instant issuedAt = Instant.now();
        Instant expiration = issuedAt.plusSeconds(expirationSeconds);
        return Jwts.builder()
                .subject(username)
                .claim(ROLE_CLAIM, role != null ? role.name() : null)
                .issuedAt(Date.from(issuedAt))
                .expiration(Date.from(expiration))
                .signWith(signingKey)
                .compact();
    }

    /**
     * Validates a token's signature and expiry.
     *
     * @param token the compact JWT string
     * @return {@code true} if the token is well-formed, correctly signed, and
     *         not expired; {@code false} otherwise
     */
    public boolean validateToken(String token) {
        try {
            parseClaims(token);
            return true;
        } catch (ExpiredJwtException ex) {
            log.debug("Rejected expired JWT: {}", ex.getMessage());
        } catch (SignatureException ex) {
            log.debug("Rejected JWT with invalid signature: {}", ex.getMessage());
        } catch (MalformedJwtException ex) {
            log.debug("Rejected malformed JWT: {}", ex.getMessage());
        } catch (UnsupportedJwtException ex) {
            log.debug("Rejected unsupported JWT: {}", ex.getMessage());
        } catch (IllegalArgumentException ex) {
            log.debug("Rejected empty or null JWT: {}", ex.getMessage());
        } catch (JwtException ex) {
            log.debug("Rejected invalid JWT: {}", ex.getMessage());
        }
        return false;
    }

    /**
     * Extracts the username (subject) from a valid token.
     *
     * @param token the compact JWT string
     * @return the token subject (username)
     * @throws JwtException if the token is invalid, malformed, expired, or wrongly signed
     */
    public String getUsername(String token) {
        return parseClaims(token).getSubject();
    }

    /**
     * Extracts the {@link Role} from the {@code role} claim of a valid token.
     *
     * @param token the compact JWT string
     * @return the role carried by the token, or {@code null} if the claim is
     *         absent or unrecognized
     * @throws JwtException if the token is invalid, malformed, expired, or wrongly signed
     */
    public Role getRole(String token) {
        String role = parseClaims(token).get(ROLE_CLAIM, String.class);
        if (role == null) {
            return null;
        }
        try {
            return Role.valueOf(role);
        } catch (IllegalArgumentException ex) {
            log.debug("Token carries unrecognized role claim: {}", role);
            return null;
        }
    }

    /**
     * Parses and verifies the token, returning its claims. Any failure to
     * verify the signature or expiry results in a {@link JwtException}.
     */
    private Claims parseClaims(String token) {
        return Jwts.parser()
                .verifyWith(signingKey)
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }
}
