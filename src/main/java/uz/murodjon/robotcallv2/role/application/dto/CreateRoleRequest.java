package uz.murodjon.robotcallv2.role.application.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;
import uz.murodjon.robotcallv2.role.domain.enums.Permission;

import java.util.Set;

public record CreateRoleRequest(
        @NotBlank @Size(max = 120) String name,
        @Size(max = 500) String description,
        @NotEmpty Set<Permission> permissions
) {
}
