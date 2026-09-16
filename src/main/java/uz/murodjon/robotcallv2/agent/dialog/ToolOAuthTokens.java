package uz.murodjon.robotcallv2.agent.dialog;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import uz.murodjon.robotcallv2.secret.domain.service.SecretPlaceholders;
import uz.murodjon.robotcallv2.shared.exception.ErrorCode;
import uz.murodjon.robotcallv2.shared.exception.ExternalServiceException;
import uz.murodjon.robotcallv2.shared.http.PublicHttpClient;
import uz.murodjon.robotcallv2.shared.http.PublicHttpRequest;
import uz.murodjon.robotcallv2.shared.http.PublicHttpResponse;
import uz.murodjon.robotcallv2.shared.util.PublicUrlGuard;
import uz.murodjon.robotcallv2.tool.domain.entity.ToolAuth;

import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Access tokens for the company tools that authenticate as an OAuth 2.0 client
 * (client_credentials, UNIVERSAL-OPERATOR-PLAN B.5).
 *
 * <p>Kept in memory per instance and per client: a token is asked for once and reused until
 * shortly before it runs out, because the token request would otherwise sit inside the pause
 * the caller listens to on every tool call. The client id and secret are read from the
 * company's secrets on each call and are never kept here.
 */
@Component
public class ToolOAuthTokens {

    private static final Logger log = LoggerFactory.getLogger(ToolOAuthTokens.class);
    private static final ObjectMapper MAPPER = new ObjectMapper();

    /** Renewed this long before it runs out, so a token is never sent in its last seconds. */
    private static final Duration EXPIRY_MARGIN = Duration.ofSeconds(60);
    /** What an answer without {@code expires_in} is taken to last. */
    private static final long DEFAULT_LIFETIME_SECONDS = 3600;
    private static final Duration TOKEN_TIMEOUT = Duration.ofSeconds(10);
    private static final int MAX_TOKEN_RESPONSE_BYTES = 64 * 1024;

    private final PublicHttpClient httpClient = PublicHttpClient.create(Duration.ofSeconds(5), TOKEN_TIMEOUT);
    private final Map<String, CachedToken> tokens = new ConcurrentHashMap<>();

    /** A token that is still good for a while, fetched from the token endpoint when there is none. */
    public String findAccessToken(long companyId, ToolAuth auth, Map<String, String> secrets)
            throws IOException, InterruptedException {
        String clientId = secrets.get(auth.clientIdSecret());
        String clientSecret = secrets.get(auth.clientSecretSecret());
        if (clientId == null || clientSecret == null) {
            throw new ExternalServiceException(ErrorCode.TOOL_AUTH_TOKEN_FAILED, "oauth2", "token endpoint",
                    "the client id or secret is not set in the company's secrets");
        }
        String key = cacheKey(companyId, auth, clientId);
        CachedToken cached = tokens.get(key);
        if (cached != null && Instant.now().isBefore(cached.renewAt())) {
            return cached.value();
        }
        CachedToken fresh = requestToken(auth, clientId, clientSecret, secrets);
        tokens.put(key, fresh);
        return fresh.value();
    }

    /** Forgets the token the API just refused (401) — revoked or rotated before it ran out. */
    public void evictToken(long companyId, ToolAuth auth, Map<String, String> secrets) {
        String clientId = secrets.get(auth.clientIdSecret());
        if (clientId != null) {
            tokens.remove(cacheKey(companyId, auth, clientId));
        }
    }

    private CachedToken requestToken(ToolAuth auth, String clientId, String clientSecret, Map<String, String> secrets)
            throws IOException, InterruptedException {
        URI uri = PublicUrlGuard.parsePublic(SecretPlaceholders.resolve(auth.tokenUrl(), secrets));
        if (uri == null) {
            throw new ExternalServiceException(ErrorCode.TOOL_AUTH_TOKEN_FAILED, "oauth2", "token endpoint",
                    "the token address is not publicly callable");
        }
        // RFC 6749 §2.3.1: the id and the secret form-encoded, then Basic — the one client
        // authentication every authorization server has to accept.
        String credentials = form(clientId) + ":" + form(clientSecret);
        String body = "grant_type=client_credentials"
                + (auth.scope() != null && !auth.scope().isBlank() ? "&scope=" + form(auth.scope().trim()) : "");
        PublicHttpRequest request = PublicHttpRequest.create("POST", uri)
                .addHeader("Authorization", "Basic "
                        + Base64.getEncoder().encodeToString(credentials.getBytes(StandardCharsets.UTF_8)))
                .addHeader("Content-Type", "application/x-www-form-urlencoded")
                .addHeader("Accept", "application/json")
                .setBody(body)
                .setTimeout(TOKEN_TIMEOUT)
                .setMaxResponseBytes(MAX_TOKEN_RESPONSE_BYTES);
        PublicHttpResponse response = httpClient.send(request);
        if (response.statusCode() < 200 || response.statusCode() >= 300) {
            // The body is not quoted: an authorization server's error can echo the client id back.
            throw new ExternalServiceException(ErrorCode.TOOL_AUTH_TOKEN_FAILED, "oauth2", uri.getHost(),
                    "HTTP " + response.statusCode());
        }
        JsonNode answer = MAPPER.readTree(response.readBodyText());
        String token = answer.path("access_token").asText(null);
        if (token == null || token.isBlank()) {
            throw new ExternalServiceException(ErrorCode.TOOL_AUTH_TOKEN_FAILED, "oauth2", uri.getHost(),
                    "the answer has no access_token");
        }
        long lifetime = answer.path("expires_in").asLong(DEFAULT_LIFETIME_SECONDS);
        log.info("Access token for a company tool obtained from {} (expires in {}s)", uri.getHost(), lifetime);
        return new CachedToken(token, Instant.now().plusSeconds(lifetime).minus(EXPIRY_MARGIN));
    }

    private static String cacheKey(long companyId, ToolAuth auth, String clientId) {
        return companyId + "|" + auth.tokenUrl() + "|" + clientId + "|" + auth.scope();
    }

    private static String form(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }

    private record CachedToken(String value, Instant renewAt) {
    }
}
