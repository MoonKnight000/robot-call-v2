package uz.murodjon.robotcallv2.security;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.core.MethodParameter;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import uz.murodjon.robotcallv2.auth.application.dto.AuthenticatedUser;
import uz.murodjon.robotcallv2.shared.exception.ForbiddenException;

import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * The tenant of a request comes from the token and from nowhere else — including the
 * case where there is no token at all, which is refused rather than defaulted.
 */
class CurrentCompanyIdArgumentResolverTest {

    private final CurrentCompanyIdArgumentResolver resolver = new CurrentCompanyIdArgumentResolver();

    @AfterEach
    void clearContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void readsTheCompanyFromTheAuthenticatedUser() {
        authenticate(new AuthenticatedUser(7L, 42L, 3L, "ADMIN", Set.of(), "Ali", "ali@example.com"));

        assertThat(resolver.resolveArgument(parameter("annotated"), null, null, null)).isEqualTo(42L);
    }

    @Test
    void refusesARequestWithNoSession() {
        assertThatThrownBy(() -> resolver.resolveArgument(parameter("annotated"), null, null, null))
                .isInstanceOf(ForbiddenException.class);
    }

    @Test
    void refusesAPrincipalThatCarriesNoCompany() {
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken("api-key", null, List.of()));

        assertThatThrownBy(() -> resolver.resolveArgument(parameter("annotated"), null, null, null))
                .isInstanceOf(ForbiddenException.class);
    }

    @Test
    void onlyAnnotatedLongParametersAreResolved() {
        assertThat(resolver.supportsParameter(parameter("annotated"))).isTrue();
        assertThat(resolver.supportsParameter(parameter("plain"))).isFalse();
    }

    private static void authenticate(AuthenticatedUser user) {
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(user, null, List.of()));
    }

    private static MethodParameter parameter(String methodName) {
        Method method = Arrays.stream(Handlers.class.getDeclaredMethods())
                .filter(candidate -> candidate.getName().equals(methodName))
                .findFirst()
                .orElseThrow();
        return new MethodParameter(method, 0);
    }

    private static final class Handlers {

        void annotated(@CurrentCompanyId long companyId) {
        }

        void plain(long companyId) {
        }
    }
}
