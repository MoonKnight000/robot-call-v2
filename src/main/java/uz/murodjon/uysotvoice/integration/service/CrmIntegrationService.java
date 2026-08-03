package uz.murodjon.uysotvoice.integration.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import uz.murodjon.uysotvoice.audit.service.AuditService;
import uz.murodjon.uysotvoice.company.service.CurrentCompany;
import uz.murodjon.uysotvoice.config.EncryptionProperties;
import uz.murodjon.uysotvoice.integration.config.UysotOAuthProperties;
import uz.murodjon.uysotvoice.integration.dto.AuthorizeUrlResponse;
import uz.murodjon.uysotvoice.integration.dto.ConnectIntegrationRequest;
import uz.murodjon.uysotvoice.integration.dto.CrmIntegration;
import uz.murodjon.uysotvoice.integration.entity.CrmIntegrationEntity;
import uz.murodjon.uysotvoice.integration.enums.CrmIntegrationStatus;
import uz.murodjon.uysotvoice.integration.enums.CrmProvider;
import uz.murodjon.uysotvoice.integration.repository.CrmIntegrationRepository;
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
import java.util.Optional;

/**
 * Uysot CRM OAuth connection (§11 settings, authorization-code flow) — each company
 * registers its own Uysot OAuth app ({@code client_id}/{@code client_secret}, entered
 * via the panel) and connects it through the standard redirect+consent dance. {@link
 * #currentAccessToken} is the runtime hot path {@code CrmClient} calls before every
 * CRM request; the rest is the settings-page CRUD plus the OAuth callback.
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

    public CrmIntegration find() {
        return repo.find(company.id()).map(CrmIntegrationService::toRow)
                .orElseGet(() -> new CrmIntegration(company.id(), CrmProvider.UYSOT,
                        null, false, CrmIntegrationStatus.NOT_CONNECTED, null));
    }

    public CrmIntegration saveCredentials(ConnectIntegrationRequest r) {
        requireCipher();
        long companyId = company.id();
        CrmIntegrationEntity entity = repo.saveCredentials(companyId, r.clientId(), cipher.encrypt(r.clientSecret()));
        audit.record("CRM_INTEGRATION_CONNECT", "crm_integration", String.valueOf(companyId), r.clientId());
        return toRow(entity);
    }

    public AuthorizeUrlResponse buildAuthorizeUrl() {
        if (!oauth.configured()) {
            throw new ExternalServiceException("uysot-oauth", "Uysot OAuth endpoints are not configured yet");
        }
        long companyId = company.id();
        CrmIntegrationEntity entity = repo.find(companyId)
                .orElseThrow(() -> new ValidationException("Save client_id/client_secret first"));
        String state = signState(companyId);
        String url = oauth.authorizeUrl()
                + "?client_id=" + encode(entity.getClientId())
                + "&redirect_uri=" + encode(oauth.redirectUri())
                + "&response_type=code"
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
            throw new ExternalServiceException("uysot-oauth", "Uysot OAuth endpoints are not configured yet");
        }
        long companyId = verifyState(state);
        CrmIntegrationEntity entity = repo.find(companyId)
                .orElseThrow(() -> new NotFoundException("crm_integration", companyId));
        try {
            String body = "grant_type=authorization_code"
                    + "&code=" + encode(code)
                    + "&redirect_uri=" + encode(oauth.redirectUri())
                    + "&client_id=" + encode(entity.getClientId())
                    + "&client_secret=" + encode(cipher.decrypt(entity.getClientSecretEnc()));
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(oauth.tokenUrl()))
                    .timeout(Duration.ofSeconds(10))
                    .header("Content-Type", "application/x-www-form-urlencoded")
                    .POST(HttpRequest.BodyPublishers.ofString(body))
                    .build();
            HttpResponse<String> resp = http.send(request, HttpResponse.BodyHandlers.ofString());
            if (resp.statusCode() / 100 != 2) {
                entity.setStatus(CrmIntegrationStatus.ERROR);
                repo.save(entity);
                throw new ExternalServiceException("uysot-oauth", "token exchange HTTP " + resp.statusCode());
            }
            applyTokenResponse(entity, resp.body());
            audit.record("CRM_INTEGRATION_CONNECTED", "crm_integration", String.valueOf(companyId), null);
        } catch (ExternalServiceException e) {
            throw e;
        } catch (Exception e) {
            entity.setStatus(CrmIntegrationStatus.ERROR);
            repo.save(entity);
            throw new ExternalServiceException("uysot-oauth", "token exchange failed: " + e.getMessage());
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
        Optional<CrmIntegrationEntity> found = repo.find(companyId);
        if (found.isEmpty() || found.get().getStatus() != CrmIntegrationStatus.CONNECTED
                || found.get().getAccessTokenEnc() == null) {
            return Optional.empty();
        }
        CrmIntegrationEntity entity = found.get();
        if (entity.getTokenExpiresAt() != null && Instant.now().isAfter(entity.getTokenExpiresAt().minus(REFRESH_SKEW))) {
            return refresh(entity);
        }
        return Optional.of(cipher.decrypt(entity.getAccessTokenEnc()));
    }

    public void disconnect() {
        long companyId = company.id();
        repo.disconnect(companyId);
        audit.record("CRM_INTEGRATION_DISCONNECT", "crm_integration", String.valueOf(companyId), null);
    }

    private Optional<String> refresh(CrmIntegrationEntity entity) {
        if (entity.getRefreshTokenEnc() == null || !oauth.configured()) {
            return Optional.empty();
        }
        try {
            String body = "grant_type=refresh_token"
                    + "&refresh_token=" + encode(cipher.decrypt(entity.getRefreshTokenEnc()))
                    + "&client_id=" + encode(entity.getClientId())
                    + "&client_secret=" + encode(cipher.decrypt(entity.getClientSecretEnc()));
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(oauth.tokenUrl()))
                    .timeout(Duration.ofSeconds(10))
                    .header("Content-Type", "application/x-www-form-urlencoded")
                    .POST(HttpRequest.BodyPublishers.ofString(body))
                    .build();
            HttpResponse<String> resp = http.send(request, HttpResponse.BodyHandlers.ofString());
            if (resp.statusCode() / 100 != 2) {
                log.warn("Uysot token refresh for company {} HTTP {}", entity.getCompanyId(), resp.statusCode());
                entity.setStatus(CrmIntegrationStatus.ERROR);
                repo.save(entity);
                return Optional.empty();
            }
            applyTokenResponse(entity, resp.body());
            return Optional.of(cipher.decrypt(entity.getAccessTokenEnc()));
        } catch (Exception e) {
            log.warn("Uysot token refresh for company {} failed: {}", entity.getCompanyId(), e.getMessage());
            entity.setStatus(CrmIntegrationStatus.ERROR);
            repo.save(entity);
            return Optional.empty();
        }
    }

    private void applyTokenResponse(CrmIntegrationEntity entity, String json) throws Exception {
        JsonNode node = mapper.readTree(json);
        String accessToken = node.hasNonNull("access_token") ? node.get("access_token").asText() : null;
        if (accessToken == null) {
            throw new ExternalServiceException("uysot-oauth", "token response had no access_token");
        }
        entity.setAccessTokenEnc(cipher.encrypt(accessToken));
        if (node.hasNonNull("refresh_token")) {
            entity.setRefreshTokenEnc(cipher.encrypt(node.get("refresh_token").asText()));
        }
        long expiresIn = node.hasNonNull("expires_in") ? node.get("expires_in").asLong() : 3600;
        entity.setTokenExpiresAt(Instant.now().plusSeconds(expiresIn));
        entity.setStatus(CrmIntegrationStatus.CONNECTED);
        entity.setConnectedAt(Instant.now());
        repo.save(entity);
    }

    /** {@code base64(companyId) + "." + hex(HMAC-SHA256)} — avoids a server-side state table (§11). */
    private String signState(long companyId) {
        String payload = String.valueOf(companyId);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(payload.getBytes(StandardCharsets.UTF_8))
                + "." + hmac(payload);
    }

    private long verifyState(String state) {
        if (state == null || !state.contains(".")) {
            throw new ValidationException("invalid state");
        }
        int dot = state.indexOf('.');
        String encodedPayload = state.substring(0, dot);
        String signature = state.substring(dot + 1);
        String payload = new String(Base64.getUrlDecoder().decode(encodedPayload), StandardCharsets.UTF_8);
        if (!MessageDigest.isEqual(hmac(payload).getBytes(StandardCharsets.UTF_8), signature.getBytes(StandardCharsets.UTF_8))) {
            throw new ValidationException("invalid state");
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
            throw new ExternalServiceException("uysot-oauth", "voice-agent.encryption.secret-key is not set");
        }
    }

    private static String encode(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }

    private static CrmIntegration toRow(CrmIntegrationEntity e) {
        return new CrmIntegration(e.getCompanyId(), e.getProvider(), e.getClientId(),
                e.getClientSecretEnc() != null, e.getStatus(), e.getConnectedAt());
    }
}
