package uz.murodjon.uysotvoice.integration.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import uz.murodjon.uysotvoice.audit.service.AuditService;
import uz.murodjon.uysotvoice.company.service.CurrentCompany;
import uz.murodjon.uysotvoice.config.EncryptionProperties;
import uz.murodjon.uysotvoice.integration.config.UysotOAuthProperties;
import uz.murodjon.uysotvoice.integration.domain.CrmIntegration;
import uz.murodjon.uysotvoice.integration.dto.AuthorizeUrlResponse;
import uz.murodjon.uysotvoice.integration.dto.ConnectIntegrationRequest;
import uz.murodjon.uysotvoice.integration.dto.CrmCatalogEntry;
import uz.murodjon.uysotvoice.integration.dto.CrmGrant;
import uz.murodjon.uysotvoice.integration.dto.CrmIntegrationRow;
import uz.murodjon.uysotvoice.integration.enums.CrmAuthMethod;
import uz.murodjon.uysotvoice.integration.enums.CrmIntegrationStatus;
import uz.murodjon.uysotvoice.integration.enums.CrmProvider;
import uz.murodjon.uysotvoice.integration.repository.CrmIntegrationRepository;
import uz.murodjon.uysotvoice.shared.exception.ErrorCode;
import uz.murodjon.uysotvoice.shared.exception.ExternalServiceException;
import uz.murodjon.uysotvoice.shared.exception.NotFoundException;
import uz.murodjon.uysotvoice.shared.exception.ValidationException;
import uz.murodjon.uysotvoice.shared.util.SecretCipher;

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
import java.util.Base64;
import java.util.HexFormat;
import java.util.List;
import java.util.Optional;

/**
 * Uysot CRM OAuth connection (§11 settings, authorization-code flow — verified against
 * Uysot's real Open API docs, report #10). One platform-wide app ({@code
 * UysotOAuthProperties}) serves every company; each company only declares its own
 * {@code app_name}/{@code grants} and goes through the standard redirect+consent dance.
 * {@link #currentAccessToken} is the runtime hot path {@code CrmClient} calls before
 * every CRM request; the rest is the settings-page CRUD plus the OAuth callback.
 */
@Service
public class CrmIntegrationService {

    private static final Logger log = LoggerFactory.getLogger(CrmIntegrationService.class);

    /** Refresh this far ahead of the real expiry, so a token never goes stale mid-request. */
    private static final Duration REFRESH_SKEW = Duration.ofMinutes(2);

    private final CrmIntegrationRepository repo;
    private final SecretCipher cipher;
    private final UysotOAuthProperties oauth;
    private final EncryptionProperties encryption;
    private final CurrentCompany company;
    private final AuditService audit;
    private final ObjectMapper mapper = new ObjectMapper();
    private final HttpClient http = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(5))
            .build();

    public CrmIntegrationService(CrmIntegrationRepository repo, SecretCipher cipher, UysotOAuthProperties oauth,
                                 EncryptionProperties encryption, CurrentCompany company, AuditService audit) {
        this.repo = repo;
        this.cipher = cipher;
        this.oauth = oauth;
        this.encryption = encryption;
        this.company = company;
        this.audit = audit;
    }

    /** The static catalog (report #10) — only {@link CrmProvider#UYSOT} is actually connectable today. */
    public List<CrmCatalogEntry> catalog() {
        return List.of(
                new CrmCatalogEntry(CrmProvider.UYSOT, "Uysot CRM", CrmAuthMethod.OAUTH, true),
                new CrmCatalogEntry(CrmProvider.BITRIX24, "Bitrix24", CrmAuthMethod.OAUTH, false),
                new CrmCatalogEntry(CrmProvider.AMOCRM, "amoCRM", CrmAuthMethod.OAUTH, false)
        );
    }

    public CrmIntegrationRow find() {
        return repo.find(company.id())
                .map(c -> CrmIntegrationRow.of(c, readGrants(c.grantsJson())))
                .orElseGet(() -> new CrmIntegrationRow(company.id(), CrmProvider.UYSOT,
                        null, List.of(), CrmIntegrationStatus.NOT_CONNECTED, null));
    }

    /** Declares this company's app identity/requested grants — the authorize URL needs both before it can be built. */
    public CrmIntegrationRow connect(ConnectIntegrationRequest r) {
        long companyId = company.id();
        CrmIntegration saved = repo.saveAppInfo(companyId, r.appName(), writeGrants(r.grants()));
        audit.record("CRM_INTEGRATION_CONNECT", "crm_integration", String.valueOf(companyId), r.appName());
        return CrmIntegrationRow.of(saved, readGrants(saved.grantsJson()));
    }

    public AuthorizeUrlResponse buildAuthorizeUrl() {
        if (!oauth.configured()) {
            throw new ExternalServiceException(ErrorCode.UYSOT_OAUTH_NOT_CONFIGURED, "uysot-oauth");
        }
        long companyId = company.id();
        CrmIntegration integration = repo.find(companyId)
                .orElseThrow(() -> new ValidationException(ErrorCode.CRM_INTEGRATION_APP_NOT_CONFIGURED));
        List<CrmGrant> grants = readGrants(integration.grantsJson());
        if (grants.isEmpty()) {
            throw new ValidationException(ErrorCode.CRM_INTEGRATION_APP_NOT_CONFIGURED);
        }
        String state = signState(companyId);
        String url = oauth.authorizeUrl()
                + "?client_id=" + encode(oauth.clientId())
                + "&app_name=" + encode(integration.appName())
                + "&redirect_url=" + encode(oauth.redirectUri())
                + "&grants=" + encode(grantsParam(grants))
                + "&state=" + encode(state);
        return new AuthorizeUrlResponse(url);
    }

    /**
     * The OAuth callback (§11) — deliberately takes no {@code CurrentCompany}: the
     * browser lands here straight from Uysot's redirect, carrying neither an API key nor
     * a JWT. {@code state} is what proves which company this belongs to.
     */
    public void handleCallback(String code, String state) {
        requireCipher();
        if (!oauth.configured()) {
            throw new ExternalServiceException(ErrorCode.UYSOT_OAUTH_NOT_CONFIGURED, "uysot-oauth");
        }
        long companyId = verifyState(state);
        repo.find(companyId).orElseThrow(() -> new NotFoundException(ErrorCode.CRM_INTEGRATION_NOT_FOUND, companyId));
        try {
            // redirect_uri here, not redirect_url — Uysot's token endpoint names the
            // same param differently than its authorize endpoint (buildAuthorizeUrl).
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
                repo.markError(companyId);
                throw new ExternalServiceException(ErrorCode.UYSOT_OAUTH_TOKEN_EXCHANGE_HTTP_ERROR, "uysot-oauth",
                        resp.statusCode());
            }
            applyTokenResponse(companyId, resp.body());
            audit.record("CRM_INTEGRATION_CONNECTED", "crm_integration", String.valueOf(companyId), null);
        } catch (ExternalServiceException e) {
            throw e;
        } catch (Exception e) {
            repo.markError(companyId);
            throw new ExternalServiceException(ErrorCode.UYSOT_OAUTH_TOKEN_EXCHANGE_FAILED, "uysot-oauth",
                    e.getMessage());
        }
    }

    /**
     * A usable access token for {@code companyId}, refreshing it first if it is at or
     * past expiry — the runtime hot path {@code CrmClient} calls before every CRM
     * request. Returns empty (never throws) for a company with no connection, no token,
     * or a failed refresh — {@code CrmClient} falls back to the static config token.
     */
    public Optional<String> currentAccessToken(long companyId) {
        if (!cipher.available()) {
            return Optional.empty();
        }
        Optional<CrmIntegration> found = repo.find(companyId);
        if (found.isEmpty() || found.get().status() != CrmIntegrationStatus.CONNECTED
                || found.get().accessTokenEnc() == null) {
            return Optional.empty();
        }
        CrmIntegration integration = found.get();
        if (integration.tokenExpiresAt() != null
                && Instant.now().isAfter(integration.tokenExpiresAt().minus(REFRESH_SKEW))) {
            return refresh(integration);
        }
        return Optional.of(cipher.decrypt(integration.accessTokenEnc()));
    }

    public void disconnect() {
        long companyId = company.id();
        repo.find(companyId).ifPresent(this::revokeUpstream);
        repo.disconnect(companyId);
        audit.record("CRM_INTEGRATION_DISCONNECT", "crm_integration", String.valueOf(companyId), null);
    }

    /**
     * Invalidates the token on Uysot's side too, not just locally — best-effort: a
     * company disconnecting must not be blocked by Uysot's revoke endpoint being slow
     * or unreachable (the local tokens are cleared regardless, see {@link #disconnect}).
     */
    private void revokeUpstream(CrmIntegration integration) {
        if (integration.accessTokenEnc() == null || !oauth.configured() || !cipher.available()
                || oauth.revokeUrl() == null || oauth.revokeUrl().isBlank()) {
            return;
        }
        try {
            String body = "token=" + encode(cipher.decrypt(integration.accessTokenEnc()))
                    + "&client_id=" + encode(oauth.clientId())
                    + "&client_secret=" + encode(oauth.clientSecret());
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(oauth.revokeUrl()))
                    .timeout(Duration.ofSeconds(10))
                    .header("Content-Type", "application/x-www-form-urlencoded")
                    .POST(HttpRequest.BodyPublishers.ofString(body))
                    .build();
            HttpResponse<String> resp = http.send(request, HttpResponse.BodyHandlers.ofString());
            if (resp.statusCode() / 100 != 2) {
                log.warn("Uysot token revoke for company {} HTTP {}", integration.companyId(), resp.statusCode());
            }
        } catch (Exception e) {
            log.warn("Uysot token revoke for company {} failed: {}", integration.companyId(), e.getMessage());
        }
    }

    private Optional<String> refresh(CrmIntegration integration) {
        if (integration.refreshTokenEnc() == null || !oauth.configured()) {
            return Optional.empty();
        }
        try {
            String body = "grant_type=refresh_token"
                    + "&refresh_token=" + encode(cipher.decrypt(integration.refreshTokenEnc()))
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
                log.warn("Uysot token refresh for company {} HTTP {}", integration.companyId(), resp.statusCode());
                repo.markError(integration.companyId());
                return Optional.empty();
            }
            CrmIntegration updated = applyTokenResponse(integration.companyId(), resp.body());
            return Optional.of(cipher.decrypt(updated.accessTokenEnc()));
        } catch (Exception e) {
            log.warn("Uysot token refresh for company {} failed: {}", integration.companyId(), e.getMessage());
            repo.markError(integration.companyId());
            return Optional.empty();
        }
    }

    private CrmIntegration applyTokenResponse(long companyId, String json) throws Exception {
        JsonNode node = mapper.readTree(json);
        String accessToken = node.hasNonNull("access_token") ? node.get("access_token").asText() : null;
        if (accessToken == null) {
            throw new ExternalServiceException(ErrorCode.UYSOT_OAUTH_TOKEN_MISSING, "uysot-oauth");
        }
        String refreshTokenEnc = node.hasNonNull("refresh_token")
                ? cipher.encrypt(node.get("refresh_token").asText()) : null;
        long expiresIn = node.hasNonNull("expires_in") ? node.get("expires_in").asLong() : 3600;
        CrmIntegration updated = repo.applyTokenResponse(companyId, cipher.encrypt(accessToken), refreshTokenEnc,
                Instant.now().plusSeconds(expiresIn));
        if (updated == null) {
            throw new IllegalStateException("crm_integration row missing for company " + companyId);
        }
        return updated;
    }

    /** {@code base64(companyId) + "." + hex(HMAC-SHA256)} — avoids a server-side state table (§11). */
    private String signState(long companyId) {
        String payload = String.valueOf(companyId);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(payload.getBytes(StandardCharsets.UTF_8))
                + "." + hmac(payload);
    }

    private long verifyState(String state) {
        if (state == null || !state.contains(".")) {
            throw new ValidationException(ErrorCode.OAUTH_STATE_INVALID);
        }
        int dot = state.indexOf('.');
        String encodedPayload = state.substring(0, dot);
        String signature = state.substring(dot + 1);
        String payload = new String(Base64.getUrlDecoder().decode(encodedPayload), StandardCharsets.UTF_8);
        if (!MessageDigest.isEqual(hmac(payload).getBytes(StandardCharsets.UTF_8), signature.getBytes(StandardCharsets.UTF_8))) {
            throw new ValidationException(ErrorCode.OAUTH_STATE_INVALID);
        }
        return Long.parseLong(payload);
    }

    private String hmac(String payload) {
        requireCipher();
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(Base64.getDecoder().decode(encryption.secretKey()), "HmacSHA256"));
            return HexFormat.of().formatHex(mac.doFinal(payload.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception e) {
            throw new IllegalStateException("state signing failed", e);
        }
    }

    private void requireCipher() {
        if (!cipher.available()) {
            throw new ExternalServiceException(ErrorCode.ENCRYPTION_KEY_NOT_SET, "uysot-oauth");
        }
    }

    private static String encode(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }

    /** Uysot's wire format for the {@code grants} param: base64(JSON), permissions as {@code PERMISSION_OPEN_API_*}. */
    private String grantsParam(List<CrmGrant> grants) {
        ArrayNode array = mapper.createArrayNode();
        for (CrmGrant grant : grants) {
            ObjectNode node = mapper.createObjectNode();
            node.put("permission", grant.permission().wireName());
            node.put("scope", grant.scope().name());
            array.add(node);
        }
        return Base64.getEncoder().encodeToString(array.toString().getBytes(StandardCharsets.UTF_8));
    }

    /** Our own storage/API shape for grants — plain JSON, not Uysot's base64-wrapped wire format. */
    private String writeGrants(List<CrmGrant> grants) {
        try {
            return mapper.writeValueAsString(grants);
        } catch (Exception e) {
            throw new IllegalStateException("could not serialize grants", e);
        }
    }

    private List<CrmGrant> readGrants(String json) {
        if (json == null || json.isBlank()) {
            return List.of();
        }
        try {
            return mapper.readValue(json, new TypeReference<List<CrmGrant>>() {
            });
        } catch (Exception e) {
            log.warn("Corrupt crm_integration.grants_json: {}", e.getMessage());
            return List.of();
        }
    }
}
