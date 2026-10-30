package uz.murodjon.robotcallv2.agent.livekit;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.stereotype.Component;
import uz.murodjon.robotcallv2.shared.exception.ConflictException;
import uz.murodjon.robotcallv2.shared.exception.ErrorCode;
import uz.murodjon.robotcallv2.shared.exception.ExternalServiceException;
import uz.murodjon.robotcallv2.shared.exception.ForbiddenException;

import javax.crypto.Mac;
import javax.crypto.SecretKey;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.Date;
import java.util.HexFormat;
import java.util.Map;

/**
 * LiveKit's server API: Twirp (JSON over HTTP POST) on the server's own port, each request
 * carrying a short-lived HS256 token signed with the API secret. The same secret signs the
 * webhooks LiveKit sends back, which is why both live here.
 */
@Component
class LiveKitApiClient {

    private static final String SERVICE = "livekit";
    private static final Duration TIMEOUT = Duration.ofSeconds(10);
    private static final Duration TOKEN_TTL = Duration.ofMinutes(5);
    /** How long a browser has to connect with the token it was given. */
    private static final Duration JOIN_TOKEN_TTL = Duration.ofMinutes(10);
    /** HS256 refuses a key under 256 bits; LiveKit's own generated secrets are longer. */
    private static final int MIN_SECRET_BYTES = 32;

    private final LiveKitProperties properties;
    private final ObjectMapper objectMapper;
    private final HttpClient httpClient = HttpClient.newBuilder().connectTimeout(TIMEOUT).build();

    LiveKitApiClient(LiveKitProperties properties, ObjectMapper objectMapper) {
        this.properties = properties;
        this.objectMapper = objectMapper;
    }

    /** Calls {@code livekit.<service>/<method>} and returns the response body. */
    JsonNode call(String service, String method, Map<String, Object> body) {
        String operation = service + "/" + method;
        HttpResponse<String> response = send(service, method, body, TIMEOUT);
        if (response.statusCode() / 100 != 2) {
            // Twirp errors are {"code": "...", "msg": "...", "meta": {...}}
            throw new ExternalServiceException(ErrorCode.LIVEKIT_REQUEST_FAILED, SERVICE, operation,
                    "HTTP " + response.statusCode() + " " + response.body());
        }
        return readBody(operation, response.body());
    }

    /**
     * The same request, handing back any HTTP answer instead of failing on a non-2xx one —
     * for the calls whose Twirp error is an outcome (a SIP call that was not answered).
     * Only the transport failing throws.
     */
    HttpResponse<String> send(String service, String method, Map<String, Object> body, Duration timeout) {
        String operation = service + "/" + method;
        HttpRequest request;
        try {
            request = HttpRequest.newBuilder(URI.create(stripTrailingSlash(properties.host()) + "/twirp/livekit." + operation))
                    .timeout(timeout)
                    .header("Content-Type", "application/json")
                    .header("Authorization", "Bearer " + createServerToken())
                    .POST(HttpRequest.BodyPublishers.ofByteArray(objectMapper.writeValueAsBytes(body)))
                    .build();
        } catch (IOException e) {
            throw new IllegalStateException("could not serialise a LiveKit request", e);
        }
        try {
            return httpClient.send(request, HttpResponse.BodyHandlers.ofString());
        } catch (IOException e) {
            throw new ExternalServiceException(ErrorCode.LIVEKIT_REQUEST_FAILED, SERVICE, e, operation, e.getMessage());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new ExternalServiceException(ErrorCode.LIVEKIT_REQUEST_FAILED, SERVICE, e, operation, "interrupted");
        }
    }

    JsonNode readBody(String operation, String body) {
        try {
            return objectMapper.readTree(body == null || body.isBlank() ? "{}" : body);
        } catch (IOException e) {
            throw new ExternalServiceException(ErrorCode.LIVEKIT_REQUEST_FAILED, SERVICE, e, operation, e.getMessage());
        }
    }

    /**
     * Checks a webhook's {@code Authorization} token: signed with our secret, issued for our
     * API key, and carrying the base64 SHA-256 of exactly this body.
     */
    void verifyWebhook(String authorization, byte[] body) {
        if (authorization == null || authorization.isBlank()) {
            throw new ForbiddenException(ErrorCode.LIVEKIT_WEBHOOK_SIGNATURE_INVALID);
        }
        String token = authorization.startsWith("Bearer ") ? authorization.substring(7) : authorization;
        Claims claims;
        try {
            claims = Jwts.parser().verifyWith(signingKey()).requireIssuer(properties.apiKey()).build()
                    .parseSignedClaims(token.trim()).getPayload();
        } catch (JwtException | IllegalArgumentException e) {
            throw new ForbiddenException(ErrorCode.LIVEKIT_WEBHOOK_SIGNATURE_INVALID);
        }
        String expected = Base64.getEncoder().encodeToString(sha256(body));
        String actual = claims.get("sha256", String.class);
        if (actual == null || !MessageDigest.isEqual(expected.getBytes(StandardCharsets.US_ASCII),
                actual.getBytes(StandardCharsets.US_ASCII))) {
            throw new ForbiddenException(ErrorCode.LIVEKIT_WEBHOOK_SIGNATURE_INVALID);
        }
    }

    /**
     * An access token that lets a browser join exactly {@code room} as {@code identity} and hear
     * it — nothing else on the server.
     *
     * @param canPublish whether the browser may speak into the room
     * @param hidden     whether the others in the room (the caller, the agent) cannot see the participant
     *                  at all — a supervisor who only listens
     */
    String createJoinToken(String room, String identity, String name, boolean canPublish, boolean hidden) {
        Instant now = Instant.now();
        return Jwts.builder()
                .issuer(properties.apiKey())
                .subject(identity)
                .claim("name", name)
                .notBefore(Date.from(now))
                .expiration(Date.from(now.plus(JOIN_TOKEN_TTL)))
                .claim("video", Map.of("roomJoin", true, "room", room, "canPublish", canPublish,
                        "canSubscribe", true, "hidden", hidden))
                .signWith(signingKey(), Jwts.SIG.HS256)
                .compact();
    }

    /**
     * Checks a request from the media bridge: the hex HMAC-SHA256 of exactly this body, keyed with the
     * API secret both share.
     */
    void verifyBridgeSignature(String signature, byte[] body) {
        if (signature == null || signature.isBlank()) {
            throw new ForbiddenException(ErrorCode.LIVEKIT_WEBHOOK_SIGNATURE_INVALID);
        }
        byte[] expected;
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(signingKey());
            expected = mac.doFinal(body);
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("HmacSHA256 unavailable", e);
        }
        String expectedHex = HexFormat.of().formatHex(expected);
        if (!MessageDigest.isEqual(expectedHex.getBytes(StandardCharsets.US_ASCII),
                signature.trim().toLowerCase().getBytes(StandardCharsets.US_ASCII))) {
            throw new ForbiddenException(ErrorCode.LIVEKIT_WEBHOOK_SIGNATURE_INVALID);
        }
    }

    /** Room admin plus SIP call rights — everything this backend asks of LiveKit. */
    private String createServerToken() {
        Instant now = Instant.now();
        return Jwts.builder()
                .issuer(properties.apiKey())
                .notBefore(Date.from(now))
                .expiration(Date.from(now.plus(TOKEN_TTL)))
                .claim("video", Map.of("roomCreate", true, "roomList", true, "roomAdmin", true))
                .claim("sip", Map.of("admin", true, "call", true))
                .signWith(signingKey(), Jwts.SIG.HS256)
                .compact();
    }

    private SecretKey signingKey() {
        if (properties.apiKey() == null || properties.apiKey().isBlank()) {
            throw new ConflictException(ErrorCode.LIVEKIT_NOT_CONFIGURED, "LIVEKIT_API_KEY");
        }
        String secret = properties.apiSecret();
        if (secret == null || secret.getBytes(StandardCharsets.UTF_8).length < MIN_SECRET_BYTES) {
            throw new ConflictException(ErrorCode.LIVEKIT_NOT_CONFIGURED, "LIVEKIT_API_SECRET");
        }
        return Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
    }

    private static byte[] sha256(byte[] body) {
        try {
            return MessageDigest.getInstance("SHA-256").digest(body);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 unavailable", e);
        }
    }

    private static String stripTrailingSlash(String host) {
        return host.endsWith("/") ? host.substring(0, host.length() - 1) : host;
    }
}
