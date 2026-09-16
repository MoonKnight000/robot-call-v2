package uz.murodjon.robotcallv2.shared.http;

import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * One request to a tenant-supplied URL, sent by {@link PublicHttpClient}.
 *
 * <p>Built fluently by the adapter that owns the call. {@code maxResponseBytes} is how much
 * of the answer is read at all; the rest is dropped with the connection rather than read
 * into memory.
 */
public final class PublicHttpRequest {

    /** What is read when the caller names no cap: plenty for a JSON answer, nowhere near a dump. */
    public static final int DEFAULT_MAX_RESPONSE_BYTES = 1024 * 1024;

    private final String method;
    private final URI uri;
    private final List<Map.Entry<String, String>> headers = new ArrayList<>();
    private byte[] body;
    private Duration timeout;
    private int maxResponseBytes = DEFAULT_MAX_RESPONSE_BYTES;

    private PublicHttpRequest(String method, URI uri) {
        this.method = method;
        this.uri = uri;
    }

    /** A request with {@code method} ({@code GET}, {@code POST}, ...) to {@code uri}. */
    public static PublicHttpRequest create(String method, URI uri) {
        return new PublicHttpRequest(method, uri);
    }

    public PublicHttpRequest addHeader(String name, String value) {
        headers.add(Map.entry(name, value));
        return this;
    }

    /** The body as UTF-8; its content type is whatever {@code Content-Type} header the caller adds. */
    public PublicHttpRequest setBody(String body) {
        this.body = body == null ? null : body.getBytes(StandardCharsets.UTF_8);
        return this;
    }

    /** How long to wait for the answer; the client's own read timeout when not set. */
    public PublicHttpRequest setTimeout(Duration timeout) {
        this.timeout = timeout;
        return this;
    }

    public PublicHttpRequest setMaxResponseBytes(int maxResponseBytes) {
        this.maxResponseBytes = maxResponseBytes;
        return this;
    }

    public String getMethod() {
        return method;
    }

    public URI getUri() {
        return uri;
    }

    public List<Map.Entry<String, String>> getHeaders() {
        return List.copyOf(headers);
    }

    /** The first value of header {@code name}, compared without case. */
    public Optional<String> findHeader(String name) {
        return headers.stream()
                .filter(header -> header.getKey().equalsIgnoreCase(name))
                .map(Map.Entry::getValue)
                .findFirst();
    }

    public byte[] getBody() {
        return body;
    }

    public Duration getTimeout() {
        return timeout;
    }

    public int getMaxResponseBytes() {
        return maxResponseBytes;
    }
}
