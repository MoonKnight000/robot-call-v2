package uz.murodjon.uysotvoice.user.dto;

import jakarta.validation.constraints.NotNull;

import uz.murodjon.uysotvoice.user.enums.UserRole;

/** {@code PUT /api/users/{id}/role} body (UI-DESIGN §10.12). */
public record UpdateUserRoleRequest(@NotNull UserRole role) {
}
