package uz.murodjon.robotcallv2.user.application.dto;

import jakarta.validation.constraints.NotNull;
import uz.murodjon.robotcallv2.user.domain.enums.UserRole;

public record UpdateUserRoleRequest(
        @NotNull UserRole role
) {
}
