package uz.murodjon.robotcallv2.shared.http;

import org.apache.hc.client5.http.classic.methods.HttpUriRequestBase;
import org.apache.hc.client5.http.config.RequestConfig;
import org.apache.hc.client5.http.impl.classic.CloseableHttpClient;
import org.apache.hc.core5.http.ClassicHttpResponse;
import org.apache.hc.core5.http.HttpEntity;
import org.apache.hc.core5.http.io.entity.ByteArrayEntity;
import org.apache.hc.core5.util.Timeout;

import java.io.IOException;
import java.io.InputStream;
import java.time.Duration;
import java.util.Arrays;
import java.util.Map;
import java.util.Set;

/**
 * Sends {@link PublicHttpRequest}s to tenant-supplied URLs over a client from
 * {@link PublicHttpClientFactory} — DNS pinned to public addresses, no redirects.
 *
 * <p>An adapter holds one of these instead of an HTTP client of its own, and reads the
 * answer already capped: what lies past {@code maxResponseBytes} is never read, the
 * connection is dropped instead.
 */
public class PublicHttpClient {

    /** Set by the transport itself; a tenant-configured value would clash with it or smuggle a second request. */
    private static final Set<String> RESTRICTED_HEADERS =
            Set.of("host", "connection", "content-length", "transfer-encoding", "expect", "upgrade");

    private final CloseableHttpClient httpClient;
    private final Duration connectTimeout;

    PublicHttpClient(CloseableHttpClient httpClient, Duration connectTimeout) {
        this.httpClient = httpClient;
        this.connectTimeout = connectTimeout;
    }

    /**
     * @param connectTimeout how long a connect may take
     * @param readTimeout    the wait for the answer when a request sets no timeout of its own
     */
    public static PublicHttpClient create(Duration connectTimeout, Duration readTimeout) {
        return new PublicHttpClient(PublicHttpClientFactory.create(connectTimeout, readTimeout), connectTimeout);
    }

    /**
     * Sends the request and reads at most its {@code maxResponseBytes} of the answer.
     *
     * <p>A host that resolves to an internal address fails here as an
     * {@link java.net.UnknownHostException}, like any host that does not resolve.
     *
     * @throws InterruptedException when the calling thread was interrupted before or during the exchange
     */
    public PublicHttpResponse send(PublicHttpRequest request) throws IOException, InterruptedException {
        if (Thread.interrupted()) {
            throw new InterruptedException("interrupted before " + request.getMethod() + " was sent");
        }
        HttpUriRequestBase message = new HttpUriRequestBase(request.getMethod(), request.getUri());
        for (Map.Entry<String, String> header : request.getHeaders()) {
            if (isSendable(header.getKey(), header.getValue())) {
                message.addHeader(header.getKey(), header.getValue());
            }
        }
        if (request.getBody() != null) {
            message.setEntity(new ByteArrayEntity(request.getBody(), null));
        }
        if (request.getTimeout() != null) {
            // A request-level config replaces the client's default, so it restates all of it.
            message.setConfig(RequestConfig.custom()
                    .setConnectionRequestTimeout(Timeout.of(connectTimeout))
                    .setResponseTimeout(Timeout.of(request.getTimeout()))
                    .setRedirectsEnabled(false)
                    .build());
        }

        ClassicHttpResponse response;
        try {
            response = httpClient.executeOpen(null, message, null);
        } catch (IOException e) {
            throw interruptedOr(e);
        }
        try {
            return read(response, message, request.getMaxResponseBytes());
        } catch (IOException e) {
            throw interruptedOr(e);
        } finally {
            closeQuietly(response);
        }
    }

    private static PublicHttpResponse read(ClassicHttpResponse response, HttpUriRequestBase message, int maxBytes)
            throws IOException {
        HttpEntity entity = response.getEntity();
        if (entity == null) {
            return new PublicHttpResponse(response.getCode(), new byte[0], false);
        }
        int cap = Math.max(0, maxBytes);
        // Not closed here: a body read to its end has already given the connection back to
        // the pool, and closing a cut-off one would read the rest of it. The caller closes
        // the response, quietly.
        InputStream content = entity.getContent();
        byte[] read = content == null ? new byte[0] : content.readNBytes(cap + 1);
        boolean truncated = read.length > cap;
        if (truncated) {
            // Drops the connection instead of draining whatever the far end still has.
            message.cancel();
        }
        return new PublicHttpResponse(response.getCode(), truncated ? Arrays.copyOf(read, cap) : read, truncated);
    }

    private static boolean isSendable(String name, String value) {
        return name != null && value != null && !name.isBlank()
                && !RESTRICTED_HEADERS.contains(name.trim().toLowerCase())
                && name.indexOf('\r') < 0 && name.indexOf('\n') < 0
                && value.indexOf('\r') < 0 && value.indexOf('\n') < 0;
    }

    /**
     * An interrupted thread surfaces from blocking socket I/O as an IOException; callers
     * tell "the call was cancelled" from "the far end failed" by the checked type, as they
     * did with the JDK client.
     */
    private static IOException interruptedOr(IOException e) throws InterruptedException {
        if (Thread.interrupted()) {
            InterruptedException interrupted = new InterruptedException("interrupted during an HTTP exchange");
            interrupted.initCause(e);
            throw interrupted;
        }
        return e;
    }

    private static void closeQuietly(ClassicHttpResponse response) {
        try {
            response.close();
        } catch (IOException ignored) {
            // A cancelled exchange fails its close; the answer was already read.
        }
    }
}
