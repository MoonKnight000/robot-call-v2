package uz.murodjon.robotcallv2.user.domain.service;

import uz.murodjon.robotcallv2.shared.exception.ErrorCode;
import uz.murodjon.robotcallv2.shared.exception.ValidationException;
import uz.murodjon.robotcallv2.user.domain.enums.UserRole;

public final class UserValidator {

    private UserValidator() {
    }

    public static void validateInviteRole(UserRole role) {
        if (role == UserRole.SUPERADMIN) {
            throw new ValidationException(ErrorCode.VALIDATION_FAILED, "SUPERADMIN role cannot be assigned via invitation");
        }
    }
}
