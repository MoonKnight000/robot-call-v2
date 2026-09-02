package uz.murodjon.robotcallv2.auth.application.dto;

import uz.murodjon.robotcallv2.user.domain.enums.UserRole;

public record AuthenticatedUser(
        long userId,
        long companyId,
        UserRole role,
        String name,
        String email
) {
}
