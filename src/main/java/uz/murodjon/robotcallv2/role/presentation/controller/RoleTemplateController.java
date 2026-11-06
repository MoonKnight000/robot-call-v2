package uz.murodjon.robotcallv2.role.presentation.controller;

import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import uz.murodjon.robotcallv2.role.application.dto.PermissionGroupRow;
import uz.murodjon.robotcallv2.role.application.dto.RoleTemplateRow;
import uz.murodjon.robotcallv2.role.application.dto.SaveRoleTemplateRequest;
import uz.murodjon.robotcallv2.security.CurrentCompanyId;
import uz.murodjon.robotcallv2.shared.api.ResponseData;

import java.util.List;

/**
 * The platform's default roles (docs/api/platform-role-templates.md). A new company receives a copy
 * of every template as its own roles; changing a template does not touch companies that already exist.
 */
@RequestMapping("/api/platform/role-templates")
public interface RoleTemplateController {

    @PreAuthorize("hasAuthority('PLATFORM_ADMIN')")
    @GetMapping
    ResponseEntity<ResponseData<List<RoleTemplateRow>>> findAll();

    /** The permission catalog for the template editor — the same list a company's role editor shows. */
    @PreAuthorize("hasAuthority('PLATFORM_ADMIN')")
    @GetMapping("/permissions")
    ResponseEntity<ResponseData<List<PermissionGroupRow>>> listPermissions();

    @PreAuthorize("hasAuthority('PLATFORM_ADMIN')")
    @GetMapping("/{id}")
    ResponseEntity<ResponseData<RoleTemplateRow>> get(@PathVariable long id);

    @PreAuthorize("hasAuthority('PLATFORM_ADMIN')")
    @PostMapping
    ResponseEntity<ResponseData<RoleTemplateRow>> create(
            @CurrentCompanyId long companyId,
            @Valid @RequestBody SaveRoleTemplateRequest request);

    /** The permission list is replaced whole. The ADMIN template must keep USER_EDIT and ROLE_EDIT. */
    @PreAuthorize("hasAuthority('PLATFORM_ADMIN')")
    @PutMapping("/{id}")
    ResponseEntity<ResponseData<RoleTemplateRow>> update(
            @CurrentCompanyId long companyId,
            @PathVariable long id,
            @Valid @RequestBody SaveRoleTemplateRequest request);

    /** The ADMIN template cannot be deleted (409). */
    @PreAuthorize("hasAuthority('PLATFORM_ADMIN')")
    @DeleteMapping("/{id}")
    ResponseEntity<ResponseData<Void>> delete(
            @CurrentCompanyId long companyId,
            @PathVariable long id);
}
