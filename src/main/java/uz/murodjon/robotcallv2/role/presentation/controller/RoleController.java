package uz.murodjon.robotcallv2.role.presentation.controller;

import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import uz.murodjon.robotcallv2.role.application.dto.CreateRoleRequest;
import uz.murodjon.robotcallv2.role.application.dto.PermissionGroupRow;
import uz.murodjon.robotcallv2.role.application.dto.RoleRow;
import uz.murodjon.robotcallv2.role.application.dto.UpdateRoleRequest;
import uz.murodjon.robotcallv2.security.CurrentCompanyId;
import uz.murodjon.robotcallv2.shared.api.ResponseData;

import java.util.List;

/**
 * Roles and their permissions inside one company (docs/api/roles.md).
 */
@RequestMapping("/api/roles")
public interface RoleController {

    @GetMapping
    @PreAuthorize("hasAuthority('ROLE_READ')")
    ResponseEntity<ResponseData<List<RoleRow>>> list(@CurrentCompanyId long companyId);

    @GetMapping("/permissions")
    @PreAuthorize("hasAuthority('ROLE_READ')")
    ResponseEntity<ResponseData<List<PermissionGroupRow>>> listPermissions();

    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('ROLE_READ')")
    ResponseEntity<ResponseData<RoleRow>> get(
            @CurrentCompanyId long companyId,
            @PathVariable long id);

    @PostMapping
    @PreAuthorize("hasAuthority('ROLE_EDIT')")
    ResponseEntity<ResponseData<RoleRow>> create(
            @CurrentCompanyId long companyId,
            @Valid @RequestBody CreateRoleRequest request);

    @PutMapping("/{id}")
    @PreAuthorize("hasAuthority('ROLE_EDIT')")
    ResponseEntity<ResponseData<RoleRow>> update(
            @CurrentCompanyId long companyId,
            @PathVariable long id,
            @Valid @RequestBody UpdateRoleRequest request);

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAuthority('ROLE_EDIT')")
    ResponseEntity<ResponseData<Void>> delete(
            @CurrentCompanyId long companyId,
            @PathVariable long id);
}
