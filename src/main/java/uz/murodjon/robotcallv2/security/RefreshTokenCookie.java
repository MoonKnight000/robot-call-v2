package uz.murodjon.robotcallv2.security;

import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;
import org.springframework.http.ResponseEntity;

import uz.murodjon.robotcallv2.auth.application.dto.LoginResponse;
import uz.murodjon.robotcallv2.shared.api.ResponseData;

import java.time.Duration;
import java.time.Instant;

/**
 * The refresh token as an {@code HttpOnly} cookie. A script injected into the console can
 * read {@code localStorage} and every JSON body, but never this cookie.
 *
 * <ul>
 *   <li>{@code Path=/api/auth} — it travels only to refresh and logout, not with every call;</li>
 *   <li>{@code SameSite=Strict} — another site's page cannot make the browser send it, which
 *       is what keeps a cookie-authenticated {@code POST /api/auth/refresh} safe without CSRF
 *       tokens;</li>
 *   <li>{@code Secure} always — there is no plain-HTTP deployment setting to hang an
 *       exception on, and browsers already treat {@code http://localhost} as secure, so local
 *       development keeps working.</li>
 * </ul>
 *
 * <p>The responses are built here rather than in the controller so that each controller
 * method stays one call.
 */
public final class RefreshTokenCookie {

    public static final String NAME = "refresh_token";
    private static final String PATH = "/api/auth";

    private RefreshTokenCookie() {
    }

    /** 200 with {@code response} as the body, and the cookie holding its refresh token for exactly as long as it lives. */
    public static ResponseEntity<ResponseData<LoginResponse>> respondWith(LoginResponse response) {
        long maxAgeSeconds = Math.max(0, Duration.between(Instant.now(), response.refreshTokenExpiresAt()).toSeconds());
        return ResponseEntity.ok()
                .header(HttpHeaders.SET_COOKIE, build(response.refreshToken(), maxAgeSeconds).toString())
                .body(ResponseData.ok(response));
    }

    /** 200 with an empty body, and a cookie the browser drops at once. */
    public static ResponseEntity<ResponseData<Void>> respondCleared() {
        return ResponseEntity.ok()
                .header(HttpHeaders.SET_COOKIE, build("", 0).toString())
                .body(ResponseData.ok(null));
    }

    private static ResponseCookie build(String value, long maxAgeSeconds) {
        return ResponseCookie.from(NAME, value)
                .httpOnly(true)
                .secure(true)
                .sameSite("Strict")
                .path(PATH)
                .maxAge(maxAgeSeconds)
                .build();
    }
}
