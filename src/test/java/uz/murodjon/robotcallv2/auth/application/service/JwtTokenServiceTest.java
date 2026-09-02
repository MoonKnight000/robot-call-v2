package uz.murodjon.robotcallv2.auth.application.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import uz.murodjon.robotcallv2.auth.application.dto.AuthenticatedUser;
import uz.murodjon.robotcallv2.auth.application.dto.IssuedToken;
import uz.murodjon.robotcallv2.auth.infrastructure.config.JwtProperties;
import uz.murodjon.robotcallv2.user.domain.enums.UserRole;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class JwtTokenServiceTest {

    private static final String SECRET = "super-secure-secret-key-that-is-at-least-256-bits-long-12345678";
    private JwtTokenService jwtTokenService;

    @BeforeEach
    void setUp() {
        JwtProperties props = new JwtProperties(SECRET, 60);
        jwtTokenService = new JwtTokenService(props);
    }

    @Test
    void issueAndParseValidToken() {
        AuthenticatedUser user = new AuthenticatedUser(
                42L, 1L, UserRole.ADMIN, "Murodjon", "murodjon@example.com");

        IssuedToken issued = jwtTokenService.issue(user);

        assertThat(issued).isNotNull();
        assertThat(issued.token()).isNotBlank();
        assertThat(issued.expiresAt()).isAfter(Instant.now());

        AuthenticatedUser parsed = jwtTokenService.parse(issued.token());

        assertThat(parsed).isNotNull();
        assertThat(parsed.userId()).isEqualTo(42L);
        assertThat(parsed.companyId()).isEqualTo(1L);
        assertThat(parsed.role()).isEqualTo(UserRole.ADMIN);
        assertThat(parsed.name()).isEqualTo("Murodjon");
        assertThat(parsed.email()).isEqualTo("murodjon@example.com");
    }

    @Test
    void parseReturnsNullForMalformedOrTamperedToken() {
        assertThat(jwtTokenService.parse("invalid.token.structure")).isNull();
        assertThat(jwtTokenService.parse("")).isNull();
    }

    @Test
    void parseReturnsNullWhenSignedWithDifferentSecret() {
        JwtTokenService otherService = new JwtTokenService(new JwtProperties("different-secret-key-value-1234567890", 60));
        AuthenticatedUser user = new AuthenticatedUser(1L, 1L, UserRole.OPERATOR, "User", "user@example.com");
        IssuedToken issued = otherService.issue(user);

        AuthenticatedUser parsed = jwtTokenService.parse(issued.token());
        assertThat(parsed).isNull();
    }

    @Test
    void throwsIllegalStateExceptionWhenSecretNotConfigured() {
        JwtTokenService unconfigured = new JwtTokenService(new JwtProperties(null, 60));
        AuthenticatedUser user = new AuthenticatedUser(1L, 1L, UserRole.OPERATOR, "User", "user@example.com");

        assertThatThrownBy(() -> unconfigured.issue(user))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("JWT signing key is not configured");

        assertThat(unconfigured.parse("any-token")).isNull();
    }
}
