package uz.murodjon.robotcallv2.user.domain.service;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import uz.murodjon.robotcallv2.shared.exception.ValidationException;
import uz.murodjon.robotcallv2.user.domain.enums.UserRole;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class UserValidatorTest {

    @Test
    void throwsValidationExceptionWhenInvitingSuperAdmin() {
        assertThatThrownBy(() -> UserValidator.validateInviteRole(UserRole.SUPERADMIN))
                .isInstanceOf(ValidationException.class);
    }

    @ParameterizedTest
    @EnumSource(value = UserRole.class, names = {"ADMIN", "OPERATOR", "VIEWER"})
    void allowsInvitingRegularRoles(UserRole role) {
        assertThatCode(() -> UserValidator.validateInviteRole(role))
                .doesNotThrowAnyException();
    }
}
