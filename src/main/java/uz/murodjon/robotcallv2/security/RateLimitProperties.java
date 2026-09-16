package uz.murodjon.robotcallv2.security;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Ceilings on the endpoints anyone on the internet can reach without a token. Bound from
 * {@code voice-agent.security.rate-limit.*}; every count is per fixed window and shared
 * across instances through Redis ({@code RateLimiter}).
 *
 * @param enabled              switch the whole filter off (tests, a load run against a
 *                             staging box) — never in production
 * @param trustForwardedFor    take the client address from the first {@code X-Forwarded-For}
 *                             hop. Only behind a reverse proxy that overwrites the header;
 *                             on a directly exposed instance a caller could forge it and
 *                             give itself a fresh allowance per request
 * @param loginPerMinute       {@code POST /api/auth/login} attempts per address per minute
 * @param loginFailuresPerUser failed logins one username may accumulate from one client
 *                             address before that username is locked for that address —
 *                             never for everyone, or anyone could lock a known account out
 * @param lockoutMinutes       how long that lock — and the failure count behind it — lasts
 * @param refreshPerMinute     {@code POST /api/auth/refresh} per address per minute
 * @param passwordResetPerHour {@code forgot-password} + {@code reset-password} per address per hour
 * @param publicWidgetPerMinute {@code /api/public/widgets/**} per address per minute
 */
@ConfigurationProperties(prefix = "voice-agent.security.rate-limit")
public record RateLimitProperties(
        boolean enabled,
        boolean trustForwardedFor,
        int loginPerMinute,
        int loginFailuresPerUser,
        int lockoutMinutes,
        int refreshPerMinute,
        int passwordResetPerHour,
        int publicWidgetPerMinute
) {
}
