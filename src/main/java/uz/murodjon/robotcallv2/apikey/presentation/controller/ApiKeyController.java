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
 * The credentials a company's own systems authenticate with, instead of a person's login
 * ({@code X-Api-Key}).
 *
 * <p>The key itself is returned by exactly one call, {@link #createApiKey}. It is stored
 * only as a hash, so a key that was not written down at that moment is gone and a new one
 * has to be issued — there is no endpoint that can show it again, on purpose.
 *
 * <p>What a key may do is its granted scopes intersected with a fixed allowlist: it may
 * move data (contacts, campaigns, the do-not-call list, the knowledge base) and read
 * everything that came of it, but it may never change configuration or touch billing. A
 * scope outside that list is dropped when the key is created rather than refused, which is
 * why {@link #grantableScopes} exists — the form offers only what a key can actually hold.
 */
@RequestMapping("/api/api-keys")
public interface ApiKeyController {

    /** Everything a key may be granted — what the creation form is filled from. */
    @GetMapping("/scopes")
    @PreAuthorize("hasAuthority('API_KEY_READ')")
    ResponseEntity<ResponseData<Set<Permission>>> grantableScopes();

    @GetMapping
    @PreAuthorize("hasAuthority('API_KEY_READ')")
    ResponseEntity<ResponseData<List<ApiKeyRow>>> apiKeys(@CurrentCompanyId long companyId);

    /**
     * Issues a key and returns it once.
     *
     * @return the key row plus {@code key} — the only response that ever carries the secret
     */
    @PostMapping
    @PreAuthorize("hasAuthority('API_KEY_EDIT')")
    ResponseEntity<ResponseData<CreatedApiKeyResponse>> createApiKey(@CurrentCompanyId long companyId,
                                                                    @Valid @RequestBody CreateApiKeyRequest request);

    /**
     * Switches a key off for good. The row is kept so the audit trail still explains what
     * it did; revoking an already-revoked key changes nothing and still answers 200.
     */
    @DeleteMapping("/{id}")
    @PreAuthorize("hasAuthority('API_KEY_EDIT')")
    ResponseEntity<ResponseData<Void>> revokeApiKey(@CurrentCompanyId long companyId, @PathVariable long id);
}
