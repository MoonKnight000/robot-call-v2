package uz.murodjon.robotcallv2.apikey.application.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import uz.murodjon.robotcallv2.apikey.application.dto.ApiKeyRow;
import uz.murodjon.robotcallv2.apikey.application.dto.CreateApiKeyRequest;
import uz.murodjon.robotcallv2.apikey.application.dto.CreatedApiKeyResponse;
import uz.murodjon.robotcallv2.apikey.application.mapper.ApiKeyMapper;
import uz.murodjon.robotcallv2.apikey.application.port.input.ApiKeyUseCase;
import uz.murodjon.robotcallv2.apikey.application.port.output.ApiKeyRepository;
import uz.murodjon.robotcallv2.apikey.domain.entity.ApiKey;
import uz.murodjon.robotcallv2.apikey.domain.service.ApiKeyScopes;
import uz.murodjon.robotcallv2.apikey.domain.service.ApiKeySecret;
import uz.murodjon.robotcallv2.audit.application.service.AuditService;
import uz.murodjon.robotcallv2.role.domain.enums.Permission;
import uz.murodjon.robotcallv2.shared.exception.ErrorCode;
import uz.murodjon.robotcallv2.shared.exception.NotFoundException;
import uz.murodjon.robotcallv2.shared.exception.ValidationException;
import uz.murodjon.robotcallv2.user.application.service.CurrentUser;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * Issues and revokes the credentials a company's own systems call with.
 *
 * <p>The secret exists for the length of {@link #createApiKey}: it is generated, hashed,
 * the hash is stored, and the key goes back in that one response. Nothing here can read it
 * again, which is the point — a key that could be recovered from the console is a key that
 * can be recovered from a stolen console session.
 */
@Service
public class ApiKeyService implements ApiKeyUseCase {

    private static final Logger log = LoggerFactory.getLogger(ApiKeyService.class);

    private final ApiKeyRepository repository;
    private final ApiKeyMapper mapper;
    private final AuditService auditService;
    private final CurrentUser currentUser;

    public ApiKeyService(ApiKeyRepository repository, ApiKeyMapper mapper, AuditService auditService,
                         CurrentUser currentUser) {
        this.repository = repository;
        this.mapper = mapper;
        this.auditService = auditService;
        this.currentUser = currentUser;
    }

    @Override
    public Set<Permission> findGrantableScopes() {
        return ApiKeyScopes.findGrantable();
    }

    @Override
    @Transactional(readOnly = true)
    public List<ApiKeyRow> findApiKeys(long companyId) {
        return repository.findByCompanyId(companyId).stream().map(mapper::toRow).toList();
    }

    @Override
    @Transactional
    public CreatedApiKeyResponse createApiKey(long companyId, CreateApiKeyRequest request) {
        Long createdBy = currentUser.id().orElse(null);
        // Intersected here as well as at authentication time, so the console shows what a
        // key actually has rather than what somebody asked for and did not get.
        Set<Permission> scopes = ApiKeyScopes.intersect(request.scopes());
        if (scopes.isEmpty()) {
            throw new ValidationException(ErrorCode.API_KEY_SCOPES_REQUIRED);
        }
        if (request.expiresAt() != null && !request.expiresAt().isAfter(Instant.now())) {
            throw new ValidationException(ErrorCode.API_KEY_EXPIRY_IN_PAST, request.expiresAt());
        }

        String prefix = ApiKeySecret.generatePrefix();
        String key = ApiKeySecret.generateKey(prefix);

        ApiKey saved = repository.save(ApiKey.issued(companyId, request.name().trim(), prefix,
                ApiKeySecret.hash(key), scopes, createdBy, request.expiresAt()));

        auditService.record(companyId, "API_KEY_CREATED", "api_key", String.valueOf(saved.id()),
                saved.name() + " (" + prefix + "), scopes: " + scopes);
        log.info("Issued API key {} for company {} with {} scopes", prefix, companyId, scopes.size());

        return new CreatedApiKeyResponse(mapper.toRow(saved), key);
    }

    @Override
    @Transactional
    public void revokeApiKey(long companyId, long id) {
        ApiKey apiKey = repository.findByCompanyIdAndId(companyId, id)
                .orElseThrow(() -> new NotFoundException(ErrorCode.API_KEY_NOT_FOUND, id));
        if (apiKey.revokedAt() != null) {
            return;
        }
        repository.updateRevokedAt(companyId, id, Instant.now());
        auditService.record(companyId, "API_KEY_REVOKED", "api_key", String.valueOf(id),
                apiKey.name() + " (" + apiKey.keyPrefix() + ")");
        log.info("Revoked API key {} of company {}", apiKey.keyPrefix(), companyId);
    }

    @Override
    @Transactional
    public ApiKey findUsableByKey(String presentedKey) {
        String prefix = ApiKeySecret.readPrefix(presentedKey);
        if (prefix == null) {
            return null;
        }
        Optional<ApiKey> found = repository.findByPrefix(prefix);
        if (found.isEmpty()) {
            return null;
        }
        ApiKey apiKey = found.get();
        Instant now = Instant.now();
        if (!apiKey.isUsableAt(now) || !ApiKeySecret.matches(presentedKey, apiKey.keyHash())) {
            return null;
        }
        // Best-effort: a key that authenticated must not be refused because the stamp
        // could not be written.
        try {
            repository.updateLastUsedAt(apiKey.id(), now);
        } catch (Exception e) {
            log.debug("Could not stamp API key {} as used: {}", prefix, e.getMessage());
        }
        return apiKey;
    }
}
