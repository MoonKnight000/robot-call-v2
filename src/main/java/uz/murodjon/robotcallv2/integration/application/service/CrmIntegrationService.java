package uz.murodjon.robotcallv2.integration.application.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import uz.murodjon.robotcallv2.audit.application.service.AuditService;
import uz.murodjon.robotcallv2.config.EncryptionProperties;
import uz.murodjon.robotcallv2.integration.application.dto.*;
import uz.murodjon.robotcallv2.integration.application.port.input.CrmIntegrationUseCase;
import uz.murodjon.robotcallv2.integration.application.port.output.CrmIntegrationRepository;
import uz.murodjon.robotcallv2.integration.domain.entity.CrmIntegration;
import uz.murodjon.robotcallv2.integration.domain.enums.CrmAuthMethod;
import uz.murodjon.robotcallv2.integration.domain.enums.CrmIntegrationStatus;
import uz.murodjon.robotcallv2.integration.domain.enums.CrmProvider;
import uz.murodjon.robotcallv2.integration.infrastructure.config.UysotOAuthProperties;
import uz.murodjon.robotcallv2.shared.exception.*;
import uz.murodjon.robotcallv2.shared.util.SecretCipher;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Duration;
import java.time.Instant;
import java.util.HexFormat;
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

    private final CrmIntegrationRepository repository;
    private final SecretCipher cipher;
    private final UysotOAuthProperties oauth;
    private final EncryptionProperties encryption;
    private final AuditService audit;
    private final ObjectMapper mapper = new ObjectMapper();
    private final HttpClient http = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(5))
            .build();

    public CrmIntegrationService(CrmIntegrationRepository repository, SecretCipher cipher, UysotOAuthProperties oauth,
                                 EncryptionProperties encryption, AuditService audit) {
        this.repository = repository;
        this.cipher = cipher;
        this.oauth = oauth;
        this.encryption = encryption;
        this.audit = audit;
    }

    @Override
    public List<CrmCatalogEntry> catalog() {
        return List.of(
                new CrmCatalogEntry(CrmProvider.UYSOT, "Uysot CRM", CrmAuthMethod.OAUTH, true),
                new CrmCatalogEntry(CrmProvider.AMOCRM, "amoCRM", CrmAuthMethod.OAUTH, true),
                new CrmCatalogEntry(CrmProvider.KOMMO, "Kommo CRM (Global amoCRM)", CrmAuthMethod.OAUTH, true),
                new CrmCatalogEntry(CrmProvider.BITRIX24, "Bitrix24", CrmAuthMethod.OAUTH, true)
        );
    }

    @Override
    public CrmIntegrationRow findByCompanyId(long companyId) {
        return repository.find(companyId)
                .map(c -> CrmIntegrationRow.of(c, readGrants(c.grantsJson())))
                .orElseGet(() -> new CrmIntegrationRow(companyId, CrmProvider.UYSOT,
                        null, List.of(), CrmIntegrationStatus.NOT_CONNECTED, null));
    }

    @Override
    public CrmIntegrationRow connect(long companyId, ConnectIntegrationRequest r) {
        CrmIntegration saved = repository.saveAppInfo(companyId, r.appName(), writeGrants(r.grants()));
        audit.record(companyId, "CRM_INTEGRATION_CONNECT", "crm_integration", String.valueOf(companyId), r.appName());
        return CrmIntegrationRow.of(saved, readGrants(saved.grantsJson()));
    }

    @Override
    public AuthorizeUrlResponse buildAuthorizeUrl(long companyId) {
        if (!oauth.configured()) {
            throw new ExternalServiceException(ErrorCode.UYSOT_OAUTH_NOT_CONFIGURED, "uysot-oauth");
        }
        CrmIntegration integration = repository.find(companyId)
                .orElseThrow(() -> new ValidationException(ErrorCode.CRM_INTEGRATION_APP_NOT_CONFIGURED));
        List<CrmGrant> grants = readGrants(integration.grantsJson());
        if (grants.isEmpty()) {
            throw new ValidationException(ErrorCode.CRM_INTEGRATION_APP_NOT_CONFIGURED);
        }
        String state = signState(companyId);
        // RFC 6749 authorization-code parameters, as Uysot's OAuth guide documents them.
        // The app's own name is not sent: the consent screen shows it from the registered
        // application, not from this link.
        String url = oauth.authorizeUrl()
                + "?response_type=code"
                + "&client_id=" + encode(oauth.clientId())
                + "&redirect_uri=" + encode(oauth.redirectUri())
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
        String base = oauth.callbackRedirectUrl() != null && !oauth.callbackRedirectUrl().isBlank()
                ? oauth.callbackRedirectUrl().trim()
                : "/";
        return URI.create(base + (base.contains("?") ? "&" : "?") + query);
    }

    private void exchangeCode(String code, String state) {
        requireCipher();
        if (!oauth.configured()) {
            throw new ExternalServiceException(ErrorCode.UYSOT_OAUTH_NOT_CONFIGURED, "uysot-oauth");
        }
        long companyId = verifyState(state);
        repository.find(companyId).orElseThrow(() -> new NotFoundException(ErrorCode.CRM_INTEGRATION_NOT_FOUND, companyId));
        try {
            String body = "grant_type=authorization_code"
                    + "&code=" + encode(code)
                    + "&redirect_uri=" + encode(oauth.redirectUri())
                    + "&client_id=" + encode(oauth.clientId())
                    + "&client_secret=" + encode(oauth.clientSecret());
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(oauth.tokenUrl()))
                    .timeout(Duration.ofSeconds(10))
                    .header("Content-Type", "application/x-www-form-urlencoded")
                    .POST(HttpRequest.BodyPublishers.ofString(body))
                    .build();
            HttpResponse<String> resp = http.send(request, HttpResponse.BodyHandlers.ofString());
            if (resp.statusCode() / 100 != 2) {
                log.warn("Uysot token exchange failed HTTP {}: {}", resp.statusCode(), resp.body());
                repository.markError(companyId);
                throw new ExternalServiceException(ErrorCode.UYSOT_OAUTH_TOKEN_EXCHANGE_HTTP_ERROR, "uysot-oauth", String.valueOf(resp.statusCode()));
            }
            JsonNode json = mapper.readTree(resp.body());
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
        currentAccessToken(companyId).ifPresent(this::revokeTokenSafely);
        repository.disconnect(companyId);
        audit.record(companyId, "CRM_INTEGRATION_DISCONNECT", "crm_integration", String.valueOf(companyId), null);
    }

    public Optional<String> currentAccessToken(long companyId) {
        return repository.find(companyId).flatMap(c -> {
            if (c.status() != CrmIntegrationStatus.CONNECTED || c.accessTokenEnc() == null) {
                return Optional.empty();
            }
            if (c.tokenExpiresAt() != null && c.tokenExpiresAt().isBefore(Instant.now().plus(REFRESH_SKEW))) {
                return refresh(companyId, c);
            }
            return Optional.ofNullable(cipher != null ? cipher.decrypt(c.accessTokenEnc()) : c.accessTokenEnc());
        });
    }

    private Optional<String> refresh(long companyId, CrmIntegration c) {
        if (cipher == null || c.refreshTokenEnc() == null || !oauth.configured()) {
            return Optional.empty();
        }
        try {
            String refresh = cipher.decrypt(c.refreshTokenEnc());
            String body = "grant_type=refresh_token"
                    + "&refresh_token=" + encode(refresh)
                    + "&client_id=" + encode(oauth.clientId())
                    + "&client_secret=" + encode(oauth.clientSecret());
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(oauth.tokenUrl()))
                    .timeout(Duration.ofSeconds(10))
                    .header("Content-Type", "application/x-www-form-urlencoded")
                    .POST(HttpRequest.BodyPublishers.ofString(body))
                    .build();
            HttpResponse<String> resp = http.send(request, HttpResponse.BodyHandlers.ofString());
            if (resp.statusCode() / 100 != 2) {
                log.warn("Uysot token refresh HTTP {} for company {}: {}", resp.statusCode(), companyId, resp.body());
                repository.markError(companyId);
                return Optional.empty();
            }
            JsonNode json = mapper.readTree(resp.body());
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
        if (oauth.revokeUrl() == null || oauth.revokeUrl().isBlank()) {
            return;
        }
        try {
            String body = "token=" + encode(token)
                    + "&client_id=" + encode(oauth.clientId())
                    + "&client_secret=" + encode(oauth.clientSecret());
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(oauth.revokeUrl()))
                    .timeout(Duration.ofSeconds(5))
                    .header("Content-Type", "application/x-www-form-urlencoded")
                    .POST(HttpRequest.BodyPublishers.ofString(body))
                    .build();
            http.send(request, HttpResponse.BodyHandlers.discarding());
        } catch (Exception e) {
            log.debug("Token revoke ignored: {}", e.getMessage());
        }
    }

    private String signState(long companyId) {
        String secret = encryption.secretKey();
        if (secret == null || secret.isBlank()) {
            return companyId + ":" + System.currentTimeMillis() + ":plain";
        }
        long ts = System.currentTimeMillis();
        String payload = companyId + ":" + ts;
        String sig = hmac(secret, payload);
        return payload + ":" + sig;
    }

    private long verifyState(String state) {
        if (state == null || state.isBlank()) {
            throw new ValidationException(ErrorCode.OAUTH_STATE_INVALID);
        }
        String[] parts = state.split(":", 3);
        if (parts.length != 3) {
            throw new ValidationException(ErrorCode.OAUTH_STATE_INVALID);
        }
        long companyId;
        long ts;
        try {
            companyId = Long.parseLong(parts[0]);
            ts = Long.parseLong(parts[1]);
        } catch (NumberFormatException e) {
            throw new ValidationException(ErrorCode.OAUTH_STATE_INVALID);
        }
        if (Math.abs(System.currentTimeMillis() - ts) > Duration.ofHours(1).toMillis()) {
            throw new ValidationException(ErrorCode.OAUTH_STATE_INVALID);
        }
        String secret = encryption.secretKey();
        if (secret != null && !secret.isBlank()) {
            String expected = hmac(secret, parts[0] + ":" + parts[1]);
            if (!MessageDigest.isEqual(expected.getBytes(StandardCharsets.UTF_8), parts[2].getBytes(StandardCharsets.UTF_8))) {
                throw new ValidationException(ErrorCode.OAUTH_STATE_INVALID);
            }
        }
        return companyId;
    }

    private String hmac(String secret, String data) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            return HexFormat.of().formatHex(mac.doFinal(data.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception e) {
            throw new IllegalStateException("HmacSHA256 unavailable", e);
        }
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
            return mapper.readValue(json, new TypeReference<List<CrmGrant>>() {});
        } catch (Exception e) {
            return List.of();
        }
    }

    private String writeGrants(List<CrmGrant> grants) {
        if (grants == null || grants.isEmpty()) {
            return "[]";
        }
        try {
            return mapper.writeValueAsString(grants);
        } catch (Exception e) {
            return "[]";
        }
    }

    private void requireCipher() {
        if (cipher == null) {
            throw new ValidationException(ErrorCode.CRM_INTEGRATION_APP_NOT_CONFIGURED);
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
