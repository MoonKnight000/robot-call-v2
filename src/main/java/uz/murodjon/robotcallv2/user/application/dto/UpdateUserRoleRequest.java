package uz.murodjon.robotcallv2.user.application.dto;

import jakarta.validation.constraints.NotNull;

public record UpdateUserRoleRequest(
        @NotNull Long roleId
) {
}
