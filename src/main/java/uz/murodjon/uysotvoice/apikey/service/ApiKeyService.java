package uz.murodjon.uysotvoice.apikey.service;

import org.springframework.stereotype.Service;

import uz.murodjon.uysotvoice.apikey.dto.ApiKey;
import uz.murodjon.uysotvoice.apikey.dto.ApiKeyFilter;
import uz.murodjon.uysotvoice.apikey.dto.CreateApiKeyRequest;
import uz.murodjon.uysotvoice.apikey.dto.CreateApiKeyResponse;
import uz.murodjon.uysotvoice.apikey.repository.ApiKeyRepository;
import uz.murodjon.uysotvoice.audit.service.AuditService;
import uz.murodjon.uysotvoice.shared.api.PageableData;
import uz.murodjon.uysotvoice.shared.exception.NotFoundException;
import uz.murodjon.uysotvoice.shared.util.Tokens;

import java.util.List;
import java.util.Optional;

/**
 * Per-company API key CRUD (§11 settings) — replaces the single global {@code X-Api-Key}
 * pair with panel-managed, revocable, per-company keys. {@link #resolve} is the runtime
 * hot path {@code config.ApiKeyFilter} calls on every request; the rest is admin CRUD.
 */
@Service
public class ApiKeyService {

    private final ApiKeyRepository repo;
    private final AuditService audit;

    public ApiKeyService(ApiKeyRepository repo, AuditService audit) {
        this.repo = repo;
        this.audit = audit;
    }

    /** The raw key is returned exactly once, here — only its hash is ever persisted. */
    public CreateApiKeyResponse create(CreateApiKeyRequest r) {
        String raw = Tokens.generate();
        String hash = Tokens.hash(raw);
        String prefix = raw.substring(0, 8);
        long id = repo.create(r.name(), r.role(), hash, prefix);
        audit.record("API_KEY_CREATE", "api_key", String.valueOf(id), r.name());
        return new CreateApiKeyResponse(requireKey(id), raw);
    }

    public PageableData<ApiKey> list(ApiKeyFilter filter) {
        List<ApiKey> rows = repo.findAll(filter);
        long total = repo.count(filter);
        return PageableData.of(rows, filter.pageOrDefault(), filter.sizeOrDefault(), total);
    }

    public void revoke(long id) {
        ApiKey key = requireKey(id);
        repo.revoke(id);
        audit.record("API_KEY_REVOKE", "api_key", String.valueOf(id), key.name());
    }

    /**
     * Authenticates a raw {@code X-Api-Key} value against the DB-backed keys — called
     * from {@code config.ApiKeyFilter} only, before any {@code Authentication} exists on
     * the request, so it never goes through {@code CurrentCompany}/audit. Returns empty
     * for an unknown, revoked, or blank key.
     */
    public Optional<ApiKey> resolve(String rawKey) {
        if (rawKey == null || rawKey.isBlank()) {
            return Optional.empty();
        }
        return repo.resolve(Tokens.hash(rawKey));
    }

    private ApiKey requireKey(long id) {
        ApiKey row = repo.find(id);
        if (row == null) {
            throw new NotFoundException("api_key", id);
        }
        return row;
    }
}
