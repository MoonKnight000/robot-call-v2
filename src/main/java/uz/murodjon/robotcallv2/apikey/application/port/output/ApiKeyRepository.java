package uz.murodjon.robotcallv2.apikey.application.port.output;

import uz.murodjon.robotcallv2.apikey.domain.entity.ApiKey;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

public interface ApiKeyRepository {

    ApiKey save(ApiKey apiKey);

    List<ApiKey> findByCompanyId(long companyId);

    Optional<ApiKey> findByCompanyIdAndId(long companyId, long id);

    /**
     * The key behind a presented prefix, whatever its state.
     *
     * <p>Revoked and expired keys come back too: whether one may still be used is the
     * caller's to decide ({@code ApiKey#isUsableAt}), and a query that hid them would
     * make a revoked key indistinguishable from one that never existed.
     */
    Optional<ApiKey> findByPrefix(String keyPrefix);

    /** Stamps the key as used. Best-effort — a request is not failed because this did not write. */
    void updateLastUsedAt(long id, Instant usedAt);

    void updateRevokedAt(long companyId, long id, Instant revokedAt);
}
