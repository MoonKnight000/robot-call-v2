package uz.murodjon.robotcallv2.scenario.application.service;

import com.fasterxml.jackson.databind.ObjectMapper;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import uz.murodjon.robotcallv2.scenario.domain.entity.FactWebhook;
import uz.murodjon.robotcallv2.scenario.domain.entity.FactWebhookRequest;
import uz.murodjon.robotcallv2.shared.util.PublicUrlGuard;

import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Map;

/**
 * Asks a company's own system for this call's facts, before the line carries any speech
 * ({@link FactWebhook}) — outbound, before the number is dialled; inbound, while the caller
 * is still hearing ringback.
 *
 * <p>Fails open, always. A call placed with facts a few days old is a worse call; a
 * campaign that stops dialling because somebody's API is down is an outage. Every failure
 * here — timeout, non-2xx, unreachable host, a body that is not JSON — is logged and the
 * call goes on with the facts it already had.
 */
@Component
public class FactWebhookClient {

    private static final Logger log = LoggerFactory.getLogger(FactWebhookClient.class);

    /** Kept short on purpose: this wait sits between claiming a target and dialling it. */
    private static final int DEFAULT_TIMEOUT_MS = 1500;
    private static final int MAX_TIMEOUT_MS = 2000;

    /**
     * A fact is a name, a sum, a date. A response larger than this is not a fact sheet, and
     * reading it all into memory per dialled call is how one bad endpoint takes the dialer
     * down with it.
     */
    private static final int MAX_RESPONSE_BYTES = 64 * 1024;

    private final ObjectMapper mapper = new ObjectMapper();

    /**
     * Redirects are never followed. {@link PublicUrlGuard} screens the host that was
     * configured, and a 302 is an endpoint choosing a second host after that check has
     * already passed — which is exactly how an SSRF filter is walked around.
     */
    private final HttpClient http = HttpClient.newBuilder()
            .connectTimeout(Duration.ofMillis(MAX_TIMEOUT_MS))
            .followRedirects(HttpClient.Redirect.NEVER)
            .build();

    /**
     * @return the endpoint's JSON body, or null when it could not be used for any reason.
     *         Callers read only their scenario's declared facts out of it
     *         ({@code CallContextMapper#overlayJson}).
     */
    public String fetchFacts(FactWebhook webhook, FactWebhookRequest call) {
        if (webhook == null || webhook.url() == null || webhook.url().isBlank() || call == null) {
            return null;
        }

        URI uri = PublicUrlGuard.parsePublic(webhook.url());
        if (uri == null) {
            return null;
        }

        boolean post = !"GET".equalsIgnoreCase(webhook.method());
        Duration timeout = Duration.ofMillis(webhook.timeoutMs() > 0
                ? Math.min(webhook.timeoutMs(), MAX_TIMEOUT_MS)
                : DEFAULT_TIMEOUT_MS);

        try {
            HttpRequest.Builder request = HttpRequest.newBuilder()
                    .uri(post ? uri : withQuery(uri, call))
                    .timeout(timeout)
                    .header("Accept", "application/json");
            applyHeaders(request, webhook.headers());

            if (post) {
                request.header("Content-Type", "application/json")
                        .POST(HttpRequest.BodyPublishers.ofString(mapper.writeValueAsString(call)));
            } else {
                request.GET();
            }

            HttpResponse<String> response = http.send(request.build(), HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() / 100 != 2) {
                log.warn("Fact webhook {} returned HTTP {} for {} {}",
                        uri.getHost(), response.statusCode(), call.direction(), call.phone());
                return null;
            }
            String json = response.body();
            if (json == null || json.isBlank()) {
                return null;
            }
            if (json.length() > MAX_RESPONSE_BYTES) {
                log.warn("Fact webhook {} returned {} chars for {} — ignored",
                        uri.getHost(), json.length(), call.phone());
                return null;
            }
            return json;
        } catch (Exception e) {
            log.warn("Fact webhook {} failed for {} {}: {}",
                    uri.getHost(), call.direction(), call.phone(), e.getMessage());
            return null;
        }
    }

    private static void applyHeaders(HttpRequest.Builder request, Map<String, String> headers) {
        if (headers == null) {
            return;
        }
        headers.forEach((name, value) -> {
            // A newline in a header value splits the request in two. These come from a
            // customer-edited scenario, so the value is not this system's to trust.
            if (name == null || value == null || name.isBlank()
                    || name.indexOf('\n') >= 0 || name.indexOf('\r') >= 0
                    || value.indexOf('\n') >= 0 || value.indexOf('\r') >= 0) {
                return;
            }
            request.header(name.trim(), value);
        });
    }

    /** The same fields the POST body carries, as query parameters; nulls are left out. */
    private static URI withQuery(URI uri, FactWebhookRequest call) {
        StringBuilder query = new StringBuilder(uri.getRawQuery() == null ? "" : uri.getRawQuery() + "&");
        query.append("direction=").append(encode(call.direction()));
        query.append("&phone=").append(encode(call.phone()));
        if (call.dialedNumber() != null) {
            query.append("&dialedNumber=").append(encode(call.dialedNumber()));
        }
        if (call.clientId() != null) {
            query.append("&clientId=").append(call.clientId());
        }
        if (call.campaignId() != null) {
            query.append("&campaignId=").append(call.campaignId());
        }
        return URI.create(uri.getScheme() + "://" + uri.getRawAuthority()
                + (uri.getRawPath() == null || uri.getRawPath().isBlank() ? "/" : uri.getRawPath())
                + "?" + query);
    }

    private static String encode(String value) {
        return value == null ? "" : URLEncoder.encode(value, StandardCharsets.UTF_8);
    }
}
