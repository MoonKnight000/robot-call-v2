package uz.murodjon.robotcallv2.user.presentation.controller;

import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import uz.murodjon.robotcallv2.role.application.dto.RoleRow;
import uz.murodjon.robotcallv2.shared.api.ResponseData;
import uz.murodjon.robotcallv2.user.application.dto.InviteUserRequest;
import uz.murodjon.robotcallv2.user.application.dto.InviteUserResponse;
import uz.murodjon.robotcallv2.user.application.dto.UpdateUserRoleRequest;
import uz.murodjon.robotcallv2.user.application.dto.UserRow;

import java.util.List;

/**
 * Platform-only management of any company's users. A new company's first administrator is
 * invited here, since nobody inside it exists yet to do so.
 */
@RequestMapping("/api/companies/{companyId}")
public interface PlatformUserController {

    @PreAuthorize("hasAuthority('PLATFORM_ADMIN')")
    @GetMapping("/users")
    ResponseEntity<ResponseData<List<UserRow>>> list(@PathVariable long companyId);

    @PreAuthorize("hasAuthority('PLATFORM_ADMIN')")
    @GetMapping("/users/{id}")
    ResponseEntity<ResponseData<UserRow>> get(@PathVariable long companyId, @PathVariable long id);

    /** The company's roles, to pick the {@code roleId} of an invite. */
    @PreAuthorize("hasAuthority('PLATFORM_ADMIN')")
    @GetMapping("/roles")
    ResponseEntity<ResponseData<List<RoleRow>>> findRoles(@PathVariable long companyId);

    @PreAuthorize("hasAuthority('PLATFORM_ADMIN')")
    @PostMapping("/users/invite")
    ResponseEntity<ResponseData<InviteUserResponse>> invite(
            @PathVariable long companyId,
            @Valid @RequestBody InviteUserRequest request);

    @PreAuthorize("hasAuthority('PLATFORM_ADMIN')")
    @PutMapping("/users/{id}/role")
    ResponseEntity<ResponseData<UserRow>> changeRole(
            @PathVariable long companyId,
            @PathVariable long id,
            @Valid @RequestBody UpdateUserRoleRequest request);

    @PreAuthorize("hasAuthority('PLATFORM_ADMIN')")
    @PutMapping("/users/{id}/block")
    ResponseEntity<ResponseData<UserRow>> block(@PathVariable long companyId, @PathVariable long id);

    @PreAuthorize("hasAuthority('PLATFORM_ADMIN')")
    @PutMapping("/users/{id}/unblock")
    ResponseEntity<ResponseData<UserRow>> unblock(@PathVariable long companyId, @PathVariable long id);
}
