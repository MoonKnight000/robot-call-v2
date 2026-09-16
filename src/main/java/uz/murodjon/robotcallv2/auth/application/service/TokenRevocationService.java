package uz.murodjon.robotcallv2.auth.application.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import uz.murodjon.robotcallv2.auth.infrastructure.config.JwtProperties;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Makes a stateless access token revocable after all. A JWT cannot be recalled, but every
 * one carries the second it was issued — so revoking a user means remembering "nothing
 * issued before now counts", and {@link JwtTokenService#parse} refuses anything older.
 *
 * <p>Kept in Redis so every instance sees the same cut-off, and mirrored in-process so the
 * check still holds for tokens this instance revoked while Redis is unreachable. The
 * entry lives exactly as long as an access token can, after which nothing it could reject
 * is valid anyway.
 */
@Service
public class TokenRevocationService {

    private static final Logger log = LoggerFactory.getLogger(TokenRevocationService.class);

    private static final String KEY_PREFIX = "auth:revoked-before:";

    private final StringRedisTemplate redisTemplate;
    private final JwtProperties jwtProperties;
    /** userId → epoch second before which this instance knows tokens are dead. */
    private final Map<Long, Long> local = new ConcurrentHashMap<>();
    /** Everything revoked on this instance since this second is in {@link #local}. */
    private final long startedAtSecond = Instant.now().getEpochSecond();

    public TokenRevocationService(StringRedisTemplate redisTemplate, JwtProperties jwtProperties) {
        this.redisTemplate = redisTemplate;
        this.jwtProperties = jwtProperties;
    }

    /** Every access token of {@code userId} issued up to now stops working. */
    public void revokeAllForUser(long userId) {
        // Seconds, like a JWT's iat: a token issued later in this same second stays valid,
        // so a logout followed by an immediate login does not eat the new token.
        long cutOff = Instant.now().getEpochSecond();
        local.merge(userId, cutOff, Math::max);
        try {
            redisTemplate.opsForValue().set(KEY_PREFIX + userId, String.valueOf(cutOff),
                    Duration.ofMinutes(jwtProperties.expiryMinutesOrDefault() + 1L));
        } catch (RuntimeException e) {
            log.warn("Redis unavailable — access tokens of user {} revoked on this instance only: {}",
                    userId, e.getMessage());
        }
    }

    /** Whether a token of {@code userId} issued at {@code issuedAt} has been revoked since. */
    public boolean isRevoked(long userId, Instant issuedAt) {
        if (issuedAt == null) {
            return false;
        }
        long issuedSecond = issuedAt.getEpochSecond();
        Long known = local.get(userId);
        if (known != null && issuedSecond < known) {
            return true;
        }
        try {
            String stored = redisTemplate.opsForValue().get(KEY_PREFIX + userId);
            return stored != null && issuedSecond < Long.parseLong(stored);
        } catch (RuntimeException e) {
            // Redis is where revocations made before this instance started (or on another
            // instance) live. The local map has already answered for every revocation made
            // here since start-up, so a token issued after that is judged by it; an older
            // one cannot be vouched for and is refused rather than waved through. Its holder
            // refreshes or logs in again and gets a token this check can answer for.
            return issuedSecond < startedAtSecond;
        }
    }
}
