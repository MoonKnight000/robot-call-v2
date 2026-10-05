package uz.murodjon.robotcallv2.scenario.infrastructure.adapter;

import com.fasterxml.jackson.databind.ObjectMapper;

import org.springframework.stereotype.Component;

import uz.murodjon.robotcallv2.scenario.application.port.output.FactWebhookFetcherPort;
import uz.murodjon.robotcallv2.scenario.domain.entity.FactWebhook;
import uz.murodjon.robotcallv2.scenario.domain.entity.FactWebhookRequest;
import uz.murodjon.robotcallv2.shared.exception.ErrorCode;
import uz.murodjon.robotcallv2.shared.exception.ExternalServiceException;
import uz.murodjon.robotcallv2.shared.http.PublicHttpClient;
import uz.murodjon.robotcallv2.shared.http.PublicHttpRequest;
import uz.murodjon.robotcallv2.shared.http.PublicHttpResponse;

import java.net.URI;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Map;

/** {@link FactWebhookFetcherPort} over the shared {@link PublicHttpClient}. */
@Component
public class HttpClientFactWebhookFetcherAdapter implements FactWebhookFetcherPort {

    /** The ceiling FactWebhookClient allows a whole request, so connecting cannot take longer. */
    private static final Duration CONNECT_TIMEOUT = Duration.ofSeconds(2);

    /**
     * A fact is a name, a sum, a date. A response larger than this is not a fact sheet, and
     * reading it all into memory per dialled call is how one bad endpoint takes the dialer
     * down with it.
     */
    private static final int MAX_RESPONSE_BYTES = 64 * 1024;

    private final ObjectMapper objectMapper;

    /**
     * DNS pinned to public addresses, and redirects never followed. PublicUrlGuard screens
     * the host that was configured, and a 302 is an endpoint choosing a second host after
     * that check has already passed — which is exactly how an SSRF filter is walked around.
     */
    private final PublicHttpClient httpClient = PublicHttpClient.create(CONNECT_TIMEOUT, CONNECT_TIMEOUT);

    public HttpClientFactWebhookFetcherAdapter(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @Override
    public String fetch(FactWebhook webhook, URI uri, FactWebhookRequest call, Duration timeout) {
        boolean post = !"GET".equalsIgnoreCase(webhook.method());
        try {
            PublicHttpRequest request = PublicHttpRequest.create(post ? "POST" : "GET", post ? uri : withQuery(uri, call))
                    .setTimeout(timeout)
                    .setMaxResponseBytes(MAX_RESPONSE_BYTES)
                    .addHeader("Accept", "application/json");
            applyHeaders(request, webhook.headers());

            if (post) {
                request.addHeader("Content-Type", "application/json")
                        .setBody(objectMapper.writeValueAsString(call));
            }

            PublicHttpResponse response = httpClient.send(request);
            if (response.statusCode() / 100 != 2) {
                throw new ExternalServiceException(ErrorCode.FACT_WEBHOOK_HTTP_STATUS, "fact-webhook",
                        uri.getHost(), response.statusCode());
            }
            if (response.truncated()) {
                throw new ExternalServiceException(ErrorCode.FACT_WEBHOOK_RESPONSE_TOO_LARGE, "fact-webhook",
                        uri.getHost(), MAX_RESPONSE_BYTES);
            }
            return response.readBodyText();
        } catch (ExternalServiceException e) {
            throw e;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new ExternalServiceException(ErrorCode.FACT_WEBHOOK_FETCH_INTERRUPTED, "fact-webhook",
                    uri.getHost());
        } catch (Exception e) {
            throw new ExternalServiceException(ErrorCode.FACT_WEBHOOK_FETCH_FAILED, "fact-webhook",
                    e, uri.getHost(), e.getMessage());
        }
    }

    private static void applyHeaders(PublicHttpRequest request, Map<String, String> headers) {
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
            request.addHeader(name.trim(), value);
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
