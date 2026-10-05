package uz.murodjon.robotcallv2.apikey.presentation.controller;

import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import uz.murodjon.robotcallv2.apikey.application.dto.ApiKeyRow;
import uz.murodjon.robotcallv2.apikey.application.dto.CreateApiKeyRequest;
import uz.murodjon.robotcallv2.apikey.application.dto.CreatedApiKeyResponse;
import uz.murodjon.robotcallv2.role.domain.enums.Permission;
import uz.murodjon.robotcallv2.security.CurrentCompanyId;
import uz.murodjon.robotcallv2.shared.api.ResponseData;

import java.util.List;
import java.util.Set;

/**
 * API Key management (docs/api/api-keys.md).
 */
@RequestMapping("/api/api-keys")
public interface ApiKeyController {

    @GetMapping("/scopes")
    @PreAuthorize("hasAuthority('API_KEY_READ')")
    ResponseEntity<ResponseData<Set<Permission>>> grantableScopes();

    @GetMapping
    @PreAuthorize("hasAuthority('API_KEY_READ')")
    ResponseEntity<ResponseData<List<ApiKeyRow>>> apiKeys(@CurrentCompanyId long companyId);

    @PostMapping
    @PreAuthorize("hasAuthority('API_KEY_EDIT')")
    ResponseEntity<ResponseData<CreatedApiKeyResponse>> createApiKey(
            @CurrentCompanyId long companyId,
            @Valid @RequestBody CreateApiKeyRequest request);

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAuthority('API_KEY_EDIT')")
    ResponseEntity<ResponseData<Void>> revokeApiKey(
            @CurrentCompanyId long companyId,
            @PathVariable long id);
}
