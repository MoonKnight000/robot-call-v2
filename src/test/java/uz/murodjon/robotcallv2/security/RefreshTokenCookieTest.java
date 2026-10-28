package uz.murodjon.robotcallv2.security;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;

import uz.murodjon.robotcallv2.auth.application.dto.LoginResponse;
import uz.murodjon.robotcallv2.shared.api.ResponseData;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;

class RefreshTokenCookieTest {

    private static final Pattern MAX_AGE = Pattern.compile("Max-Age=(\\d+)");

    @Test
    void issuedTokenIsSetAsAnHttpOnlyStrictCookieLivingAsLongAsTheToken() {
        LoginResponse login = new LoginResponse("access", Instant.now().plusSeconds(3600), "refresh-value",
                Instant.now().plus(Duration.ofDays(30)), null);

        ResponseEntity<ResponseData<LoginResponse>> response = cookie(true).respondWith(login);

        String cookie = response.getHeaders().getFirst(HttpHeaders.SET_COOKIE);
        assertThat(cookie).startsWith("refresh_token=refresh-value;")
                .contains("Path=/api/auth", "HttpOnly", "Secure", "SameSite=Strict");
        assertThat(maxAge(cookie)).isBetween(Duration.ofDays(30).toSeconds() - 5, Duration.ofDays(30).toSeconds());
        assertThat(response.getBody()).isNotNull();
    }

    @Test
    void clearedCookieExpiresAtOnceOnTheSamePath() {
        String cookie = cookie(true).respondCleared().getHeaders().getFirst(HttpHeaders.SET_COOKIE);

        assertThat(cookie).startsWith("refresh_token=;").contains("Path=/api/auth", "HttpOnly", "Secure");
        assertThat(maxAge(cookie)).isZero();
    }

    @Test
    void secureFlagIsDroppedOnlyWhenTurnedOff() {
        String cookie = cookie(false).respondCleared().getHeaders().getFirst(HttpHeaders.SET_COOKIE);

        assertThat(cookie).contains("HttpOnly", "SameSite=Strict").doesNotContain("Secure");
    }

    private static RefreshTokenCookie cookie(boolean secure) {
        return new RefreshTokenCookie(new SecurityProperties(List.of(), secure));
    }

    private static long maxAge(String cookie) {
        Matcher matcher = MAX_AGE.matcher(cookie);
        assertThat(matcher.find()).isTrue();
        return Long.parseLong(matcher.group(1));
    }
}
