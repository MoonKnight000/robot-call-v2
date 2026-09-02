package uz.murodjon.robotcallv2.integration.application.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import uz.murodjon.robotcallv2.audit.application.service.AuditService;
import uz.murodjon.robotcallv2.company.application.service.CurrentCompany;
import uz.murodjon.robotcallv2.config.EncryptionProperties;
import uz.murodjon.robotcallv2.integration.application.dto.*;
import uz.murodjon.robotcallv2.integration.application.port.input.CrmIntegrationUseCase;
import uz.murodjon.robotcallv2.integration.application.port.output.CrmIntegrationRepository;
import uz.murodjon.robotcallv2.integration.domain.entity.CrmIntegration;
import uz.murodjon.robotcallv2.integration.domain.enums.CrmAuthMethod;
import uz.murodjon.robotcallv2.integration.domain.enums.CrmIntegrationStatus;
import uz.murodjon.robotcallv2.integration.domain.enums.CrmProvider;
import uz.murodjon.robotcallv2.integration.infrastructure.config.UysotOAuthProperties;
import uz.murodjon.robotcallv2.shared.exception.ErrorCode;
import uz.murodjon.robotcallv2.shared.exception.ExternalServiceException;
import uz.murodjon.robotcallv2.shared.exception.NotFoundException;
import uz.murodjon.robotcallv2.shared.exception.ValidationException;
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
import java.util.Base64;
import java.util.HexFormat;
import java.util.List;
import java.util.Optional;

/**
 * Uysot CRM, amoCRM, Kommo & Bitrix24 OAuth connection (§11 settings).
 */
@Service
public class CrmIntegrationService implements CrmIntegrationUseCase {

    private static final Logger log = LoggerFactory.getLogger(CrmIntegrationService.class);
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
    public CrmIntegrationRow find() {
        return repo.find(company.id())
                .map(c -> CrmIntegrationRow.of(c, readGrants(c.grantsJson())))
                .orElseGet(() -> new CrmIntegrationRow(company.id(), CrmProvider.UYSOT,
                        null, List.of(), CrmIntegrationStatus.NOT_CONNECTED, null));
    }

    @Override
    public CrmIntegrationRow connect(ConnectIntegrationRequest r) {
        long companyId = company.id();
        CrmIntegration saved = repo.saveAppInfo(companyId, r.appName(), writeGrants(r.grants()));
        audit.record("CRM_INTEGRATION_CONNECT", "crm_integration", String.valueOf(companyId), r.appName());
        return CrmIntegrationRow.of(saved, readGrants(saved.grantsJson()));
    }

    @Override
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

    @Override
    public void handleCallback(String code, String state) {
        requireCipher();
        if (!oauth.configured()) {
            throw new ExternalServiceException(ErrorCode.UYSOT_OAUTH_NOT_CONFIGURED, "uysot-oauth");
        }
        long companyId = verifyState(state);
        repo.find(companyId).orElseThrow(() -> new NotFoundException(ErrorCode.CRM_INTEGRATION_NOT_FOUND, companyId));
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
                repo.markError(companyId);
                throw new ExternalServiceException(ErrorCode.UYSOT_OAUTH_TOKEN_EXCHANGE_HTTP_ERROR, "uysot-oauth", String.valueOf(resp.statusCode()));
            }
            JsonNode json = mapper.readTree(resp.body());
            String access = json.path("access_token").asText(null);
            String refresh = json.path("refresh_token").asText(null);
            int expiresIn = json.path("expires_in").asInt(86400);
            if (access == null || access.isBlank()) {
                repo.markError(companyId);
                throw new ExternalServiceException(ErrorCode.UYSOT_OAUTH_TOKEN_MISSING, "uysot-oauth");
            }
            Instant expiresAt = Instant.now().plusSeconds(expiresIn);
            repo.applyTokenResponse(companyId, cipher.encrypt(access),
                    refresh != null ? cipher.encrypt(refresh) : null, expiresAt);
            audit.record("CRM_INTEGRATION_CONNECTED", "crm_integration", String.valueOf(companyId), null);
        } catch (ExternalServiceException e) {
            throw e;
        } catch (Exception e) {
            log.warn("Uysot callback failed for company {}: {}", companyId, e.getMessage());
            repo.markError(companyId);
            throw new ExternalServiceException(ErrorCode.UYSOT_OAUTH_TOKEN_EXCHANGE_FAILED, "uysot-oauth", e.getMessage());
        }
    }

    @Override
    public void disconnect() {
        long companyId = company.id();
        Optional<CrmIntegration> current = repo.find(companyId);
        if (current.isEmpty()) {
            return;
        }
        currentAccessToken(companyId).ifPresent(this::revokeTokenSafely);
        repo.disconnect(companyId);
        audit.record("CRM_INTEGRATION_DISCONNECT", "crm_integration", String.valueOf(companyId), null);
    }

    public Optional<String> currentAccessToken(long companyId) {
        return repo.find(companyId).flatMap(c -> {
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
                repo.markError(companyId);
                return Optional.empty();
            }
            JsonNode json = mapper.readTree(resp.body());
            String newAccess = json.path("access_token").asText(null);
            String newRefresh = json.path("refresh_token").asText(null);
            int expiresIn = json.path("expires_in").asInt(86400);
            if (newAccess == null) {
                repo.markError(companyId);
                return Optional.empty();
            }
            Instant expiresAt = Instant.now().plusSeconds(expiresIn);
            repo.applyTokenResponse(companyId, cipher.encrypt(newAccess),
                    newRefresh != null ? cipher.encrypt(newRefresh) : c.refreshTokenEnc(), expiresAt);
            return Optional.of(newAccess);
        } catch (Exception e) {
            log.warn("Uysot token refresh failed for company {}: {}", companyId, e.getMessage());
            repo.markError(companyId);
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

    private String grantsParam(List<CrmGrant> grants) {
        ArrayNode arr = mapper.createArrayNode();
        for (CrmGrant g : grants) {
            ObjectNode node = arr.addObject();
            node.put("permission", "PERMISSION_OPEN_API_" + g.permission().name());
            node.put("scope", g.scope().name());
        }
        try {
            return Base64.getEncoder().encodeToString(mapper.writeValueAsBytes(arr));
        } catch (Exception e) {
            return "";
        }
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
}
