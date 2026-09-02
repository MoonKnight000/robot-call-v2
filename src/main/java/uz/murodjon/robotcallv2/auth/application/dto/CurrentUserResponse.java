package uz.murodjon.robotcallv2.auth.application.dto;

import uz.murodjon.robotcallv2.user.domain.enums.UserRole;

public record CurrentUserResponse(
        long id,
        String name,
        String username,
        String email,
        UserRole role,
        long companyId,
        String companyName
) {
}
