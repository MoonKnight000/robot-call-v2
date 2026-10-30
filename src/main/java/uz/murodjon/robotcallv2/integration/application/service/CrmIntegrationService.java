package uz.murodjon.robotcallv2.integration.application.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import uz.murodjon.robotcallv2.audit.application.port.input.AuditUseCase;
import uz.murodjon.robotcallv2.company.application.port.input.CompanyUseCase;
import uz.murodjon.robotcallv2.company.domain.entity.Company;
import uz.murodjon.robotcallv2.integration.application.dto.*;
import uz.murodjon.robotcallv2.integration.application.port.input.CrmIntegrationUseCase;
import uz.murodjon.robotcallv2.integration.application.port.output.CrmIntegrationRepository;
import uz.murodjon.robotcallv2.integration.application.port.output.OAuthStateStorePort;
import uz.murodjon.robotcallv2.integration.application.port.output.UysotOAuthPort;
import uz.murodjon.robotcallv2.integration.domain.entity.CrmConnection;
import uz.murodjon.robotcallv2.integration.domain.entity.CrmIntegration;
import uz.murodjon.robotcallv2.integration.domain.entity.OAuthTokenReply;
import uz.murodjon.robotcallv2.integration.domain.enums.CrmAuthMethod;
import uz.murodjon.robotcallv2.integration.domain.enums.CrmIntegrationStatus;
import uz.murodjon.robotcallv2.integration.domain.enums.CrmProvider;
import uz.murodjon.robotcallv2.integration.domain.enums.IntegrationCatalogStatus;
import uz.murodjon.robotcallv2.integration.domain.service.CrmIntegrationValidator;
import uz.murodjon.robotcallv2.integration.infrastructure.config.UysotOAuthProperties;
import uz.murodjon.robotcallv2.shared.exception.*;
import uz.murodjon.robotcallv2.shared.integration.IntegrationProvider;
import uz.murodjon.robotcallv2.shared.util.PublicUrlGuard;
import uz.murodjon.robotcallv2.shared.util.SecretCipher;
import uz.murodjon.robotcallv2.shared.util.Tokens;

import java.net.URI;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * Uysot CRM, amoCRM, Kommo & Bitrix24 OAuth connection (§11 settings).
 */
@Service
public class CrmIntegrationService implements CrmIntegrationUseCase {

    private static final Logger log = LoggerFactory.getLogger(CrmIntegrationService.class);
    private static final Duration REFRESH_SKEW = Duration.ofMinutes(2);
    /** How long a person has to get through the consent screen. */
    private static final Duration STATE_TTL = Duration.ofHours(1);

    private final CrmIntegrationRepository repository;
    private final SecretCipher cipher;
    private final UysotOAuthProperties uysotOAuthProperties;
    private final OAuthStateStorePort oauthStateStorePort;
    private final AuditUseCase audit;
    private final CompanyUseCase companyUseCase;
    private final ObjectMapper objectMapper;
    private final UysotOAuthPort uysotOAuthPort;

    public CrmIntegrationService(CrmIntegrationRepository repository, SecretCipher cipher, UysotOAuthProperties uysotOAuthProperties,
                                 OAuthStateStorePort oauthStateStorePort, AuditUseCase audit, CompanyUseCase companyUseCase,
                                 UysotOAuthPort uysotOAuthPort,
                                 ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
        this.repository = repository;
        this.cipher = cipher;
        this.uysotOAuthProperties = uysotOAuthProperties;
        this.oauthStateStorePort = oauthStateStorePort;
        this.audit = audit;
        this.companyUseCase = companyUseCase;
        this.uysotOAuthPort = uysotOAuthPort;
    }

    @Override
    public List<IntegrationCatalogEntry> findCatalog(long companyId) {
        Company company = companyUseCase.findById(companyId);
        return List.of(
                catalogEntry(IntegrationProvider.UYSOT, "Uysot CRM", CrmAuthMethod.OAUTH, company),
                catalogEntry(IntegrationProvider.AMOCRM, "amoCRM", CrmAuthMethod.API_TOKEN, company),
                catalogEntry(IntegrationProvider.KOMMO, "Kommo CRM (Global amoCRM)", CrmAuthMethod.API_TOKEN, company),
                catalogEntry(IntegrationProvider.BITRIX24, "Bitrix24", CrmAuthMethod.API_TOKEN, company),
                catalogEntry(IntegrationProvider.GOOGLE_DRIVE, "Google Drive", null, company),
                catalogEntry(IntegrationProvider.NOTION, "Notion", null, company),
                catalogEntry(IntegrationProvider.ASTERISK, "Asterisk", null, company));
    }

    private static IntegrationCatalogEntry catalogEntry(IntegrationProvider provider, String displayName,
                                                        CrmAuthMethod authMethod, Company company) {
        IntegrationCatalogStatus status = !provider.available() ? IntegrationCatalogStatus.COMING_SOON
                : company != null && !company.isEnabled(provider) ? IntegrationCatalogStatus.DISABLED_BY_PLATFORM
                : IntegrationCatalogStatus.AVAILABLE;
        return new IntegrationCatalogEntry(provider, displayName, provider.category(), authMethod, status,
                status == IntegrationCatalogStatus.AVAILABLE);
    }

    @Override
    public CrmIntegrationRow findByCompanyId(long companyId) {
        return repository.find(companyId)
                .map(c -> CrmIntegrationRow.of(c, readGrants(c.grantsJson()), accountHostOf(c)))
                .orElseGet(() -> new CrmIntegrationRow(companyId, CrmProvider.UYSOT,
                        null, List.of(), CrmIntegrationStatus.NOT_CONNECTED, null, null));
    }

    @Override
    public CrmIntegrationRow connect(long companyId, ConnectIntegrationRequest request) {
        companyUseCase.requireIntegrationEnabled(companyId, IntegrationProvider.UYSOT);
        CrmIntegrationValidator.validateAppName(request.appName());
        CrmIntegration saved = repository.saveAppInfo(companyId, request.appName(), writeGrants(request.grants()));
        audit.record(companyId, "CRM_INTEGRATION_CONNECT", "crm_integration", String.valueOf(companyId), request.appName());
        return CrmIntegrationRow.of(saved, readGrants(saved.grantsJson()), null);
    }

    @Override
    public CrmIntegrationRow connectAccount(long companyId, ConnectCrmAccountRequest request) {
        companyUseCase.requireIntegrationEnabled(companyId, IntegrationProvider.valueOf(request.provider().name()));
        CrmIntegrationValidator.validateAccount(request.provider(), request.accessToken());
        String baseUrl = CrmIntegrationValidator.normalizeBaseUrl(request.baseUrl());
        // Screened here so a bad address is a 400 now — and again by the connector before
        // every request, because what a host resolves to can change after it was saved.
        URI address = PublicUrlGuard.parsePublic(baseUrl);
        if (address == null || !"https".equalsIgnoreCase(address.getScheme())) {
            throw new ValidationException(ErrorCode.CRM_BASE_URL_INVALID);
        }
        if (!cipher.available()) {
            throw new ConflictException(ErrorCode.ENCRYPTION_KEY_NOT_SET);
        }
        String accessToken = request.accessToken() == null || request.accessToken().isBlank()
                ? null
                : request.accessToken().trim();
        CrmIntegration saved = repository.saveCredentials(companyId, CrmIntegration.credentials(request.provider(),
                cipher.encrypt(baseUrl), accessToken != null ? cipher.encrypt(accessToken) : null));
        // The host only: for Bitrix24 the full address is the credential.
        audit.record(companyId, "CRM_INTEGRATION_CONNECT", "crm_integration", String.valueOf(companyId),
                request.provider().name() + " " + address.getHost());
        return CrmIntegrationRow.of(saved, List.of(), address.getHost());
    }

    @Override
    public AuthorizeUrlResponse buildAuthorizeUrl(long companyId) {
        companyUseCase.requireIntegrationEnabled(companyId, IntegrationProvider.UYSOT);
        // The tokens this flow ends with are stored encrypted — no key, no point starting it.
        requireCipher();
        if (!uysotOAuthProperties.configured()) {
            throw new ExternalServiceException(ErrorCode.UYSOT_OAUTH_NOT_CONFIGURED, "uysot-oauth");
        }
        CrmIntegration integration = repository.find(companyId)
                .orElseThrow(() -> new ValidationException(ErrorCode.CRM_INTEGRATION_APP_NOT_CONFIGURED));
        List<CrmGrant> grants = readGrants(integration.grantsJson());
        if (grants.isEmpty()) {
            throw new ValidationException(ErrorCode.CRM_INTEGRATION_APP_NOT_CONFIGURED);
        }
        String state = issueState(companyId);
        // RFC 6749 authorization-code parameters, as Uysot's OAuth guide documents them.
        // The app's own name is not sent: the consent screen shows it from the registered
        // application, not from this link.
        String url = uysotOAuthProperties.authorizeUrl()
                + "?response_type=code"
                + "&client_id=" + encode(uysotOAuthProperties.clientId())
                + "&redirect_uri=" + encode(uysotOAuthProperties.redirectUri())
                + "&scope=" + encodeQueryValue(scopeParam(grants))
                + "&state=" + encode(state);
        return new AuthorizeUrlResponse(url);
    }

    @Override
    public URI handleCallback(String code, String state) {
        try {
            exchangeCode(code, state);
            return returnUrl("crm=connected");
        } catch (AppException e) {
            // Answered as a redirect, not rethrown: whoever is at the other end of this is a
            // person's browser that has just come back from Uysot's consent screen, and an
            // error envelope would leave them on a blank JSON page. The reason travels as
            // the same ErrorCode name the rest of the API reports, so the settings screen
            // translates it exactly as it would any other failure. A non-AppException is a
            // bug and still surfaces as a 500 — it has no message worth showing anyone.
            log.warn("Uysot callback failed: {}", e.code());
            return returnUrl("crm=error&reason=" + e.code().name());
        }
    }

    /**
     * Where the browser goes once the exchange is over. Blank configuration falls back to
     * the application root, which serves the bundled panel — never to nothing, because a
     * 302 with no location is a dead end.
     */
    private URI returnUrl(String query) {
        String base = uysotOAuthProperties.callbackRedirectUrl() != null && !uysotOAuthProperties.callbackRedirectUrl().isBlank()
                ? uysotOAuthProperties.callbackRedirectUrl().trim()
                : "/";
        return URI.create(base + (base.contains("?") ? "&" : "?") + query);
    }

    private void exchangeCode(String code, String state) {
        requireCipher();
        if (!uysotOAuthProperties.configured()) {
            throw new ExternalServiceException(ErrorCode.UYSOT_OAUTH_NOT_CONFIGURED, "uysot-oauth");
        }
        long companyId = consumeState(state);
        repository.find(companyId).orElseThrow(() -> new NotFoundException(ErrorCode.CRM_INTEGRATION_NOT_FOUND, companyId));
        try {
            OAuthTokenReply resp = uysotOAuthPort.exchangeCode(code);
            if (resp.statusCode() / 100 != 2) {
                // Status only: the body of a failed token exchange can still carry a token
                // or the error's echo of our credentials, and this line goes to disk.
                log.warn("Uysot token exchange failed HTTP {} for company {}", resp.statusCode(), companyId);
                repository.markError(companyId);
                throw new ExternalServiceException(ErrorCode.UYSOT_OAUTH_TOKEN_EXCHANGE_HTTP_ERROR, "uysot-oauth", String.valueOf(resp.statusCode()));
            }
            JsonNode json = objectMapper.readTree(resp.body());
            String access = json.path("access_token").asText(null);
            String refresh = json.path("refresh_token").asText(null);
            int expiresIn = json.path("expires_in").asInt(86400);
            if (access == null || access.isBlank()) {
                repository.markError(companyId);
                throw new ExternalServiceException(ErrorCode.UYSOT_OAUTH_TOKEN_MISSING, "uysot-oauth");
            }
            Instant expiresAt = Instant.now().plusSeconds(expiresIn);
            repository.applyTokenResponse(companyId, cipher.encrypt(access),
                    refresh != null ? cipher.encrypt(refresh) : null, expiresAt);
            audit.record(companyId, "CRM_INTEGRATION_CONNECTED", "crm_integration", String.valueOf(companyId), null);
        } catch (ExternalServiceException e) {
            throw e;
        } catch (Exception e) {
            log.warn("Uysot callback failed for company {}: {}", companyId, e.getMessage());
            repository.markError(companyId);
            throw new ExternalServiceException(ErrorCode.UYSOT_OAUTH_TOKEN_EXCHANGE_FAILED, "uysot-oauth", e.getMessage());
        }
    }

    @Override
    public void disconnect(long companyId) {
        Optional<CrmIntegration> current = repository.find(companyId);
        if (current.isEmpty()) {
            return;
        }
        findCurrentAccessToken(companyId).ifPresent(this::revokeTokenSafely);
        repository.disconnect(companyId);
        audit.record(companyId, "CRM_INTEGRATION_DISCONNECT", "crm_integration", String.valueOf(companyId), null);
    }

    @Override
    public List<CrmGrant> findGrants(long companyId) {
        return repository.find(companyId).map(c -> readGrants(c.grantsJson())).orElse(List.of());
    }

    @Override
    public Optional<CrmConnection> findConnection(long companyId) {
        return repository.find(companyId).map(integration -> {
            if (integration.provider() == CrmProvider.UYSOT) {
                // Uysot's address is platform configuration; only the token is the company's.
                return new CrmConnection(CrmProvider.UYSOT, null, findCurrentAccessToken(companyId).orElse(null));
            }
            if (integration.status() != CrmIntegrationStatus.CONNECTED || integration.baseUrlEnc() == null) {
                return new CrmConnection(integration.provider(), null, null);
            }
            return new CrmConnection(integration.provider(), cipher.decrypt(integration.baseUrlEnc()),
                    integration.accessTokenEnc() != null ? cipher.decrypt(integration.accessTokenEnc()) : null);
        });
    }

    /**
     * The host of a connected amoCRM, Kommo or Bitrix24 account, for the settings screen —
     * never the address itself, which for Bitrix24 is the credential.
     */
    private String accountHostOf(CrmIntegration integration) {
        if (integration.baseUrlEnc() == null || !cipher.available()) {
            return null;
        }
        try {
            return URI.create(cipher.decrypt(integration.baseUrlEnc())).getHost();
        } catch (RuntimeException e) {
            log.warn("Stored CRM address of company {} could not be read", integration.companyId());
            return null;
        }
    }

    @Override
    public Optional<String> findCurrentAccessToken(long companyId) {
        return repository.find(companyId).flatMap(c -> {
            // Uysot's token only: an amoCRM or Bitrix24 credential must never reach Uysot's
            // API, its debtor import or its revoke endpoint.
            if (c.provider() != CrmProvider.UYSOT
                    || c.status() != CrmIntegrationStatus.CONNECTED || c.accessTokenEnc() == null) {
                return Optional.empty();
            }
            if (c.tokenExpiresAt() != null && c.tokenExpiresAt().isBefore(Instant.now().plus(REFRESH_SKEW))) {
                return refresh(companyId, c);
            }
            return Optional.ofNullable(cipher != null ? cipher.decrypt(c.accessTokenEnc()) : c.accessTokenEnc());
        });
    }

    private Optional<String> refresh(long companyId, CrmIntegration c) {
        if (!cipher.available() || c.refreshTokenEnc() == null || !uysotOAuthProperties.configured()) {
            return Optional.empty();
        }
        try {
            OAuthTokenReply resp = uysotOAuthPort.refreshToken(cipher.decrypt(c.refreshTokenEnc()));
            if (resp.statusCode() / 100 != 2) {
                log.warn("Uysot token refresh HTTP {} for company {}", resp.statusCode(), companyId);
                repository.markError(companyId);
                return Optional.empty();
            }
            JsonNode json = objectMapper.readTree(resp.body());
            String newAccess = json.path("access_token").asText(null);
            String newRefresh = json.path("refresh_token").asText(null);
            int expiresIn = json.path("expires_in").asInt(86400);
            if (newAccess == null) {
                repository.markError(companyId);
                return Optional.empty();
            }
            Instant expiresAt = Instant.now().plusSeconds(expiresIn);
            repository.applyTokenResponse(companyId, cipher.encrypt(newAccess),
                    newRefresh != null ? cipher.encrypt(newRefresh) : c.refreshTokenEnc(), expiresAt);
            return Optional.of(newAccess);
        } catch (Exception e) {
            log.warn("Uysot token refresh failed for company {}: {}", companyId, e.getMessage());
            repository.markError(companyId);
            return Optional.empty();
        }
    }

    private void revokeTokenSafely(String token) {
        if (uysotOAuthProperties.revokeUrl() == null || uysotOAuthProperties.revokeUrl().isBlank()) {
            return;
        }
        try {
            uysotOAuthPort.revokeToken(token);
        } catch (Exception e) {
            log.debug("Token revoke ignored: {}", e.getMessage());
        }
    }

    /**
     * A random, one-time state remembered server-side against the company. The callback
     * is public and takes the company from the state, so the state must be impossible to
     * forge and good for one callback only — the old signed {@code companyId:time} value
     * could be replayed for an hour.
     */
    private String issueState(long companyId) {
        String state = Tokens.generate();
        if (!oauthStateStorePort.save(state, companyId, STATE_TTL)) {
            throw new ExternalServiceException(ErrorCode.OAUTH_STATE_STORE_UNAVAILABLE, "redis");
        }
        return state;
    }

    private long consumeState(String state) {
        if (state == null || state.isBlank()) {
            throw new ValidationException(ErrorCode.OAUTH_STATE_INVALID);
        }
        Long companyId = oauthStateStorePort.consume(state);
        if (companyId == null) {
            throw new ValidationException(ErrorCode.OAUTH_STATE_INVALID);
        }
        return companyId;
    }

    /**
     * The {@code scope} the consent screen asks the company to approve: space-separated
     * {@code PERMISSION:SCOPE} pairs, e.g.
     * {@code PERMISSION_OPEN_API_LEAD:READ PERMISSION_OPEN_API_CONTRACT:READ}.
     *
     * <p>What comes back may be narrower than what was asked for — the company can approve
     * a subset — so the token response's own {@code scope} is what a connection may
     * actually do, not this string.
     */
    private static String scopeParam(List<CrmGrant> grants) {
        return grants.stream()
                .map(grant -> grant.permission().wireName() + ":" + grant.scope().name())
                .collect(Collectors.joining(" "));
    }

    private List<CrmGrant> readGrants(String json) {
        if (json == null || json.isBlank()) {
            return List.of();
        }
        try {
            return objectMapper.readValue(json, new TypeReference<List<CrmGrant>>() {});
        } catch (Exception e) {
            return List.of();
        }
    }

    private String writeGrants(List<CrmGrant> grants) {
        if (grants == null || grants.isEmpty()) {
            return "[]";
        }
        try {
            return objectMapper.writeValueAsString(grants);
        } catch (Exception e) {
            return "[]";
        }
    }

    /**
     * The injected cipher is never null — a missing key shows as {@code !available()}. Checked
     * before the consent flow starts and before its callback spends the one-time state, so a
     * server without a key answers ENCRYPTION_KEY_NOT_SET instead of failing at the encrypt.
     */
    private void requireCipher() {
        if (!cipher.available()) {
            throw new ConflictException(ErrorCode.ENCRYPTION_KEY_NOT_SET);
        }
    }

    private static String encode(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }

    /**
     * Like {@link #encode} but with spaces as {@code %20} rather than {@code +}.
     *
     * <p>{@code +} means a space only to a reader that form-decodes, and the authorization
     * page is a URL the browser follows, not a form post. A scope arriving as
     * {@code A:READ+B:READ} is one unknown grant, and the flow fails at the consent screen
     * with "invalid scope" — the encoding is the whole of the difference.
     */
    private static String encodeQueryValue(String value) {
        return encode(value).replace("+", "%20");
    }
}
