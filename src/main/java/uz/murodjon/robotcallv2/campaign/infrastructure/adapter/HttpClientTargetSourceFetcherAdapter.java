package uz.murodjon.robotcallv2.campaign.infrastructure.adapter;

import org.springframework.stereotype.Component;

import uz.murodjon.robotcallv2.campaign.application.port.output.TargetSourceFetcherPort;
import uz.murodjon.robotcallv2.campaign.domain.enums.TargetSourceMethod;
import uz.murodjon.robotcallv2.shared.exception.ErrorCode;
import uz.murodjon.robotcallv2.shared.exception.ExternalServiceException;
import uz.murodjon.robotcallv2.shared.http.PublicHttpClient;
import uz.murodjon.robotcallv2.shared.http.PublicHttpRequest;
import uz.murodjon.robotcallv2.shared.http.PublicHttpResponse;

import java.net.URI;
import java.time.Duration;

/**
 * {@link TargetSourceFetcherPort} over the shared {@link PublicHttpClient}: DNS pinned to
 * public addresses and redirects never followed — the screening applies to the host that
 * was configured, and a redirect is an endpoint choosing a second host after that check.
 */
@Component
public class HttpClientTargetSourceFetcherAdapter implements TargetSourceFetcherPort {

    /** Generous: nobody is on the line waiting for this. */
    private static final Duration TIMEOUT = Duration.ofSeconds(30);

    /** A list this large is a misconfiguration, and holding it in memory is how one lands. */
    private static final int MAX_RESPONSE_BYTES = 16 * 1024 * 1024;

    private final PublicHttpClient httpClient = PublicHttpClient.create(TIMEOUT, TIMEOUT);

    @Override
    public String fetch(URI uri, TargetSourceMethod method, String requestBody, String authHeaderName,
                        String authHeaderValue) {
        try {
            PublicHttpRequest request = PublicHttpRequest.create(method == TargetSourceMethod.POST ? "POST" : "GET", uri)
                    .setTimeout(TIMEOUT)
                    .setMaxResponseBytes(MAX_RESPONSE_BYTES)
                    .addHeader("Accept", "application/json");
            applyAuthHeader(request, authHeaderName, authHeaderValue);
            if (method == TargetSourceMethod.POST) {
                request.addHeader("Content-Type", "application/json").setBody(requestBody);
            }

            PublicHttpResponse response = httpClient.send(request);
            if (response.statusCode() / 100 != 2) {
                throw new ExternalServiceException(ErrorCode.TARGET_SOURCE_HTTP_STATUS, "target-source",
                        uri.getHost(), response.statusCode());
            }
            if (response.truncated()) {
                throw new ExternalServiceException(ErrorCode.TARGET_SOURCE_RESPONSE_TOO_LARGE, "target-source",
                        uri.getHost(), MAX_RESPONSE_BYTES);
            }
            String body = response.readBodyText();
            if (body.isBlank()) {
                throw new ExternalServiceException(ErrorCode.TARGET_SOURCE_RESPONSE_EMPTY, "target-source",
                        uri.getHost());
            }
            return body;
        } catch (ExternalServiceException e) {
            throw e;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new ExternalServiceException(ErrorCode.TARGET_SOURCE_FETCH_INTERRUPTED, "target-source",
                    uri.getHost());
        } catch (Exception e) {
            throw new ExternalServiceException(ErrorCode.TARGET_SOURCE_FETCH_FAILED, "target-source",
                    e, uri.getHost(), e.getMessage());
        }
    }

    private static void applyAuthHeader(PublicHttpRequest request, String name, String value) {
        // A newline in a header value splits the request in two, and both halves come from
        // a tenant-edited settings page.
        if (name == null || value == null || name.isBlank() || value.isBlank()
                || name.indexOf('\n') >= 0 || name.indexOf('\r') >= 0
                || value.indexOf('\n') >= 0 || value.indexOf('\r') >= 0) {
            return;
        }
        request.addHeader(name.trim(), value);
    }
}
