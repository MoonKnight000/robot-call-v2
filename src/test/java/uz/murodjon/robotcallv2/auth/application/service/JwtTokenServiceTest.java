package uz.murodjon.robotcallv2.auth.application.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import uz.murodjon.robotcallv2.auth.application.dto.AuthenticatedUser;
import uz.murodjon.robotcallv2.auth.application.dto.IssuedToken;
import uz.murodjon.robotcallv2.auth.infrastructure.config.JwtProperties;
import uz.murodjon.robotcallv2.role.domain.enums.Permission;
import uz.murodjon.robotcallv2.shared.exception.ConflictException;

import java.time.Instant;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class JwtTokenServiceTest {

    private static final String SECRET = "super-secure-secret-key-that-is-at-least-256-bits-long-12345678";
    private TokenRevocationService revocation;
    private JwtTokenService jwtTokenService;

    @BeforeEach
    void setUp() {
        JwtProperties props = new JwtProperties(SECRET, 60);
        revocation = mock(TokenRevocationService.class);
        jwtTokenService = new JwtTokenService(props, revocation);
    }

    @Test
    void issueAndParseValidToken() {
        AuthenticatedUser user = new AuthenticatedUser(
                42L, 1L, 7L, "ADMIN",
                Set.of(Permission.CAMPAIGN_READ, Permission.CAMPAIGN_EDIT),
                "Murodjon", "murodjon@example.com");

        IssuedToken issued = jwtTokenService.issue(user);

        assertThat(issued).isNotNull();
        assertThat(issued.token()).isNotBlank();
        assertThat(issued.expiresAt()).isAfter(Instant.now());

        AuthenticatedUser parsed = jwtTokenService.parse(issued.token());

        assertThat(parsed).isNotNull();
        assertThat(parsed.userId()).isEqualTo(42L);
        assertThat(parsed.companyId()).isEqualTo(1L);
        assertThat(parsed.roleId()).isEqualTo(7L);
        assertThat(parsed.roleCode()).isEqualTo("ADMIN");
        assertThat(parsed.permissions())
                .containsExactlyInAnyOrder(Permission.CAMPAIGN_READ, Permission.CAMPAIGN_EDIT);
        assertThat(parsed.name()).isEqualTo("Murodjon");
        assertThat(parsed.email()).isEqualTo("murodjon@example.com");
    }

    /** The token carries the short codes, not the enum names — that is what keeps it small. */
    @Test
    void tokenCarriesPermissionCodes() {
        AuthenticatedUser user = operator(Set.of(Permission.CAMPAIGN_READ));

        String payload = new String(java.util.Base64.getUrlDecoder()
                .decode(jwtTokenService.issue(user).token().split("\\.")[1]));

        assertThat(payload).contains(Permission.CAMPAIGN_READ.code());
        assertThat(payload).doesNotContain(Permission.CAMPAIGN_READ.name());
    }

    @Test
    void parseReturnsNullForMalformedOrTamperedToken() {
        assertThat(jwtTokenService.parse("invalid.token.structure")).isNull();
        assertThat(jwtTokenService.parse("")).isNull();
    }

    @Test
    void parseReturnsNullWhenSignedWithDifferentSecret() {
        JwtTokenService otherService = new JwtTokenService(
                new JwtProperties("different-secret-key-value-1234567890", 60), revocation);
        IssuedToken issued = otherService.issue(operator(Set.of(Permission.CAMPAIGN_READ)));

        AuthenticatedUser parsed = jwtTokenService.parse(issued.token());
        assertThat(parsed).isNull();
    }

    /** A valid signature is not enough once the user has been revoked since the token was issued. */
    @Test
    void parseReturnsNullForARevokedUser() {
        IssuedToken issued = jwtTokenService.issue(operator(Set.of(Permission.CAMPAIGN_READ)));
        when(revocation.isRevoked(eq(1L), any())).thenReturn(true);

        assertThat(jwtTokenService.parse(issued.token())).isNull();
    }

    @Test
    void refusesToIssueWhenSecretNotConfigured() {
        JwtTokenService unconfigured = new JwtTokenService(new JwtProperties(null, 60), revocation);
        AuthenticatedUser user = operator(Set.of(Permission.CAMPAIGN_READ));

        assertThatThrownBy(() -> unconfigured.issue(user))
                .isInstanceOf(ConflictException.class)
                .hasMessageContaining("voice-agent.security.jwt.secret is not set");

        assertThat(unconfigured.parse("any-token")).isNull();
    }

    private static AuthenticatedUser operator(Set<Permission> permissions) {
        return new AuthenticatedUser(1L, 1L, 2L, "OPERATOR", permissions, "User", "user@example.com");
    }
}
