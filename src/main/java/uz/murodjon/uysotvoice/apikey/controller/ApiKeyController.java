package uz.murodjon.uysotvoice.apikey.controller;

import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;

import uz.murodjon.uysotvoice.apikey.dto.ApiKey;
import uz.murodjon.uysotvoice.apikey.dto.ApiKeyFilter;
import uz.murodjon.uysotvoice.apikey.dto.CreateApiKeyRequest;
import uz.murodjon.uysotvoice.apikey.dto.CreateApiKeyResponse;
import uz.murodjon.uysotvoice.shared.api.PageableData;
import uz.murodjon.uysotvoice.shared.api.ResponseData;

/**
 * Per-company API key CRUD (§11 settings) — scoped to the caller's own company via
 * {@code CurrentCompany} (JWT), not a path id. ADMIN-only ({@code SecurityConfig}).
 */
@RequestMapping("/api/settings/api-keys")
public interface ApiKeyController {

    @PostMapping
    ResponseEntity<ResponseData<CreateApiKeyResponse>> create(@Valid @RequestBody CreateApiKeyRequest r);

    @PostMapping("/list")
    ResponseEntity<ResponseData<PageableData<ApiKey>>> list(@Valid @RequestBody ApiKeyFilter filter);

    @DeleteMapping("/{id}")
    ResponseEntity<ResponseData<Void>> revoke(@PathVariable long id);
}
