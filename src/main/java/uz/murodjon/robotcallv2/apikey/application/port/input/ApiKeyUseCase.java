package uz.murodjon.robotcallv2.apikey.application.port.input;

import uz.murodjon.robotcallv2.apikey.application.dto.ApiKeyRow;
import uz.murodjon.robotcallv2.apikey.application.dto.CreateApiKeyRequest;
import uz.murodjon.robotcallv2.apikey.application.dto.CreatedApiKeyResponse;
import uz.murodjon.robotcallv2.apikey.domain.entity.ApiKey;
import uz.murodjon.robotcallv2.role.domain.enums.Permission;

import java.util.List;
import java.util.Set;

/** Issuing, listing and revoking the credentials a company's own systems call with. */
public interface ApiKeyUseCase {

    /** Everything a key may be granted, for the form that creates one. */
    Set<Permission> findGrantableScopes();

    List<ApiKeyRow> findApiKeys(long companyId);

    /** The only call that ever returns the key itself. */
    CreatedApiKeyResponse createApiKey(long companyId, CreateApiKeyRequest request);

    /** Switches a key off for good. A revoked key is kept, so its history stays readable. */
    void revokeApiKey(long companyId, long id);

    /**
     * Authenticates a presented key, or returns null when it is unknown, revoked, expired
     * or simply wrong.
     *
     * <p>Called by {@code ApiKeyAuthFilter} on every request that carries one. It returns
     * the key rather than an identity because building that identity — intersecting the
     * scopes, naming the principal — is the filter's job, and this stays the one place
     * that decides whether a secret is genuine.
     */
    ApiKey findUsableByKey(String presentedKey);
}
