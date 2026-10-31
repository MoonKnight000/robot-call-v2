package uz.murodjon.robotcallv2.auth.application.service;

import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import uz.murodjon.robotcallv2.auth.application.dto.AuthenticatedUser;
import uz.murodjon.robotcallv2.auth.application.dto.IssuedToken;
import uz.murodjon.robotcallv2.auth.application.port.input.AccessTokenUseCase;
import uz.murodjon.robotcallv2.auth.infrastructure.config.JwtProperties;
import uz.murodjon.robotcallv2.role.domain.enums.Permission;
import uz.murodjon.robotcallv2.shared.exception.ConflictException;
import uz.murodjon.robotcallv2.shared.exception.ErrorCode;
import uz.murodjon.robotcallv2.shared.util.Tokens;
import uz.murodjon.robotcallv2.user.domain.enums.UserType;

import javax.crypto.SecretKey;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Date;
import java.util.EnumSet;
import java.util.Set;
import java.util.stream.Collectors;

@Service
public class JwtTokenService implements AccessTokenUseCase {

    private static final Logger log = LoggerFactory.getLogger(JwtTokenService.class);

    /** Comma-separated {@link Permission#code()} values — the short form keeps the token small. */
    private static final String PERMISSIONS_CLAIM = "perms";

    private final JwtProperties jwtProperties;
    private final TokenRevocationService revocation;
    private final SecretKey key;

    public JwtTokenService(JwtProperties jwtProperties, TokenRevocationService revocation) {
        this.jwtProperties = jwtProperties;
        this.revocation = revocation;
        this.key = jwtProperties.configured() ? Keys.hmacShaKeyFor(Tokens.sha256(jwtProperties.secret())) : null;
        if (!jwtProperties.configured()) {
            log.error("voice-agent.security.jwt.secret is blank, shorter than 32 characters or still a CHANGE_ME "
                    + "placeholder — no user can log in until it is set (e.g. openssl rand -base64 48).");
        }
    }

    public IssuedToken issue(AuthenticatedUser user) {
        if (key == null) {
            throw new ConflictException(ErrorCode.JWT_KEY_NOT_SET);
        }
        Instant now = Instant.now();
        Instant expiresAt = now.plus(jwtProperties.expiryMinutesOrDefault(), ChronoUnit.MINUTES);
        String token = Jwts.builder()
                .subject(String.valueOf(user.userId()))
                .claim("companyId", user.companyId())
                .claim("roleId", user.roleId())
                .claim("role", user.roleCode())
                .claim("type", user.type() != null ? user.type().name() : null)
                .claim(PERMISSIONS_CLAIM, encodePermissions(user.permissions()))
                .claim("name", user.name())
                .claim("email", user.email())
                .issuedAt(Date.from(now))
                .expiration(Date.from(expiresAt))
                .signWith(key)
                .compact();
        return new IssuedToken(token, expiresAt);
    }

    @Override
    public AuthenticatedUser parse(String token) {
        if (key == null) {
            return null;
        }
        try {
            var claims = Jwts.parser().verifyWith(key).build().parseSignedClaims(token).getPayload();
            long userId = Long.parseLong(claims.getSubject());
            Date issuedAt = claims.getIssuedAt();
            if (revocation.isRevoked(userId, issuedAt == null ? null : issuedAt.toInstant())) {
                return null;
            }
            Number companyId = (Number) claims.get("companyId");
            Number roleId = (Number) claims.get("roleId");
            String typeStr = claims.get("type", String.class);
            UserType type = typeStr != null ? UserType.valueOf(typeStr) : (companyId == null ? UserType.PLATFORM_ADMIN : UserType.COMPANY_USER);
            return new AuthenticatedUser(
                    userId,
                    companyId == null ? null : companyId.longValue(),
                    roleId == null ? null : roleId.longValue(),
                    claims.get("role", String.class),
                    decodePermissions(claims.get(PERMISSIONS_CLAIM, String.class)),
                    claims.get("name", String.class),
                    claims.get("email", String.class),
                    type);
        } catch (JwtException | IllegalArgumentException e) {
            return null;
        }
    }

    private static String encodePermissions(Set<Permission> permissions) {
        return permissions.stream().map(Permission::code).collect(Collectors.joining(","));
    }

    /**
     * Unknown codes are dropped rather than rejected: a token issued before a permission
     * was renamed keeps working for everything else it was granted.
     */
    private static Set<Permission> decodePermissions(String encoded) {
        EnumSet<Permission> permissions = EnumSet.noneOf(Permission.class);
        if (encoded == null || encoded.isBlank()) {
            return permissions;
        }
        for (String code : encoded.split(",")) {
            Permission permission = Permission.findByCode(code.trim());
            if (permission != null) {
                permissions.add(permission);
            }
        }
        return permissions;
    }
}
