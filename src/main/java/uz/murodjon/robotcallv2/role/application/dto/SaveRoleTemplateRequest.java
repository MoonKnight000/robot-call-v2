package uz.murodjon.robotcallv2.role.application.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;
import uz.murodjon.robotcallv2.role.domain.enums.Permission;

import java.util.Set;

/**
 * Body of both create and update of a role template. {@code assignableByCompany = false} means only
 * platform staff may hand the copied role to a user (DEVELOPER today); null is treated as true.
 */
public record SaveRoleTemplateRequest(
        @NotBlank @Size(max = 120) String name,
        @Size(max = 500) String description,
        Boolean assignableByCompany,
        @NotEmpty Set<Permission> permissions
) {
}
