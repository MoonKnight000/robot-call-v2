package uz.murodjon.uysotvoice.auth.service;

import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import uz.murodjon.uysotvoice.auth.config.JwtProperties;
import uz.murodjon.uysotvoice.auth.dto.AuthenticatedUser;
import uz.murodjon.uysotvoice.auth.dto.IssuedToken;
import uz.murodjon.uysotvoice.user.enums.UserRole;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Date;

/**
 * Issues and verifies the JWTs {@code config.JwtAuthFilter} authenticates requests with
 * (ROADMAP E.1). The configured secret is hashed to exactly 256 bits before use — HS256
 * needs a key of that length, and hashing means any non-empty configured secret works
 * instead of silently failing (or throwing a confusing {@code WeakKeyException}) when an
 * operator sets a short one.
 */
@Service
public class JwtTokenService {

    private static final Logger log = LoggerFactory.getLogger(JwtTokenService.class);

    private final JwtProperties props;
    private final SecretKey key;

    public JwtTokenService(JwtProperties props) {
        this.props = props;
        this.key = props.configured() ? Keys.hmacShaKeyFor(sha256(props.secret())) : null;
        if (!props.configured()) {
            log.error("voice-agent.security.jwt.secret is not set — no user can log in until it is.");
        }
    }

    public IssuedToken issue(AuthenticatedUser user) {
        if (key == null) {
            throw new IllegalStateException("JWT signing key is not configured");
        }
        Instant now = Instant.now();
        Instant expiresAt = now.plus(props.expiryMinutesOrDefault(), ChronoUnit.MINUTES);
        String token = Jwts.builder()
                .subject(String.valueOf(user.userId()))
                .claim("companyId", user.companyId())
                .claim("role", user.role().name())
                .claim("name", user.name())
                .claim("email", user.email())
                .issuedAt(Date.from(now))
                .expiration(Date.from(expiresAt))
                .signWith(key)
                .compact();
        return new IssuedToken(token, expiresAt);
    }

    /** {@code null} if the token is missing, expired, malformed or signed by another key. */
    public AuthenticatedUser parse(String token) {
        if (key == null) {
            return null;
        }
        try {
            var claims = Jwts.parser().verifyWith(key).build().parseSignedClaims(token).getPayload();
            return new AuthenticatedUser(
                    Long.parseLong(claims.getSubject()),
                    ((Number) claims.get("companyId")).longValue(),
                    UserRole.valueOf(claims.get("role", String.class)),
                    claims.get("name", String.class),
                    claims.get("email", String.class));
        } catch (JwtException | IllegalArgumentException e) {
            return null;
        }
    }

    private static byte[] sha256(String value) {
        try {
            return MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 not available", e);
        }
    }
}
