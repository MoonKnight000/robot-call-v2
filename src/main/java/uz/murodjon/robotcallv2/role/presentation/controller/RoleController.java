package uz.murodjon.robotcallv2.role.presentation.controller;

import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;

import uz.murodjon.robotcallv2.role.application.dto.CreateRoleRequest;
import uz.murodjon.robotcallv2.role.application.dto.PermissionGroupRow;
import uz.murodjon.robotcallv2.role.application.dto.RoleRow;
import uz.murodjon.robotcallv2.role.application.dto.UpdateRoleRequest;
import uz.murodjon.robotcallv2.shared.api.ResponseData;

import java.util.List;

/**
 * Roles and their permissions inside one company. The seeded system roles (DEVELOPER,
 * ADMIN, OPERATOR, VIEWER) are returned like any other but cannot be edited or deleted;
 * a company may define up to {@code RoleValidator.MAX_CUSTOM_ROLES} of its own.
 */
@RequestMapping("/api/roles")
public interface RoleController {

    /** Every role of the current company, each with how many users hold it. */
    @GetMapping
    @PreAuthorize("hasAuthority('ROLE_READ')")
    ResponseEntity<ResponseData<List<RoleRow>>> list();

    /** The permission catalog grouped by page — what the role editor renders. */
    @GetMapping("/permissions")
    @PreAuthorize("hasAuthority('ROLE_READ')")
    ResponseEntity<ResponseData<List<PermissionGroupRow>>> listPermissions();

    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('ROLE_READ')")
    ResponseEntity<ResponseData<RoleRow>> get(@PathVariable long id);

    @PostMapping
    @PreAuthorize("hasAuthority('ROLE_EDIT')")
    ResponseEntity<ResponseData<RoleRow>> create(@Valid @RequestBody CreateRoleRequest request);

    /**
     * Replaces the role name, description and permission set. Everyone holding the role is
     * logged out, because their access token still carries the previous permissions.
     */
    @PutMapping("/{id}")
    @PreAuthorize("hasAuthority('ROLE_EDIT')")
    ResponseEntity<ResponseData<RoleRow>> update(@PathVariable long id,
                                                 @Valid @RequestBody UpdateRoleRequest request);

    /** Refused with 409 while any user still holds the role. */
    @DeleteMapping("/{id}")
    @PreAuthorize("hasAuthority('ROLE_EDIT')")
    ResponseEntity<ResponseData<Void>> delete(@PathVariable long id);
}
