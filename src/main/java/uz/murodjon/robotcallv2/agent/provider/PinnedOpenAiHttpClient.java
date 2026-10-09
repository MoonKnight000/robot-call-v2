package uz.murodjon.robotcallv2.agent.provider;

import com.openai.core.RequestOptions;
import com.openai.core.Timeout;
import com.openai.core.http.Headers;
import com.openai.core.http.HttpClient;
import com.openai.core.http.HttpRequest;
import com.openai.core.http.HttpRequestBody;
import com.openai.core.http.HttpResponse;
import com.openai.errors.OpenAIIoException;
import org.apache.hc.client5.http.classic.methods.HttpUriRequestBase;
import org.apache.hc.client5.http.config.RequestConfig;
import org.apache.hc.client5.http.impl.classic.CloseableHttpClient;
import org.apache.hc.core5.http.ClassicHttpResponse;
import org.apache.hc.core5.http.ContentType;
import org.apache.hc.core5.http.Header;
import org.apache.hc.core5.http.HttpEntity;
import org.apache.hc.core5.http.io.entity.ByteArrayEntity;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.time.Duration;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * The OpenAI SDK's transport for a company's own LLM. The address is the tenant's, so every
 * request goes out over a {@code PublicHttpClientFactory} client — DNS pinned to public
 * addresses, no redirects — instead of the SDK's own OkHttp client, which would resolve the
 * host again after it was screened. Responses are handed back unread, so a streamed turn
 * reaches the caller token by token.
 */
final class PinnedOpenAiHttpClient implements HttpClient {

    /** Set by the transport itself. */
    private static final Set<String> TRANSPORT_HEADERS =
            Set.of("host", "connection", "content-length", "transfer-encoding");

    /** A streamed turn blocks its thread for as long as the model writes. */
    private static final ExecutorService ASYNC = Executors.newVirtualThreadPerTaskExecutor();

    private final CloseableHttpClient httpClient;
    private final Duration connectTimeout;

    PinnedOpenAiHttpClient(CloseableHttpClient httpClient, Duration connectTimeout) {
        this.httpClient = httpClient;
        this.connectTimeout = connectTimeout;
    }

    @Override
    public HttpResponse execute(HttpRequest request, RequestOptions requestOptions) {
        HttpUriRequestBase message = new HttpUriRequestBase(request.method().name(), URI.create(request.url()));
        Headers headers = request.headers();
        for (String name : headers.names()) {
            if (!TRANSPORT_HEADERS.contains(name.toLowerCase(Locale.ROOT))) {
                headers.values(name).forEach(value -> message.addHeader(name, value));
            }
        }
        HttpRequestBody body = request.body();
        if (body != null) {
            ByteArrayOutputStream bytes = new ByteArrayOutputStream();
            body.writeTo(bytes);
            message.setEntity(new ByteArrayEntity(bytes.toByteArray(),
                    body.contentType() == null ? null : ContentType.parse(body.contentType())));
        }
        Timeout timeout = requestOptions.getTimeout();
        if (timeout != null && timeout.read() != null) {
            // A request-level config replaces the client's default, so it restates all of it.
            message.setConfig(RequestConfig.custom()
                    .setConnectionRequestTimeout(org.apache.hc.core5.util.Timeout.of(connectTimeout))
                    .setResponseTimeout(org.apache.hc.core5.util.Timeout.of(timeout.read()))
                    .setRedirectsEnabled(false)
                    .build());
        }
        try {
            return new StreamedResponse(httpClient.executeOpen(null, message, null));
        } catch (IOException e) {
            // Never the URL: it is the tenant's, and the message reaches the logs.
            throw new OpenAIIoException("the company's LLM could not be reached: " + e.getClass().getSimpleName(), e);
        }
    }

    @Override
    public CompletableFuture<HttpResponse> executeAsync(HttpRequest request, RequestOptions requestOptions) {
        return CompletableFuture.supplyAsync(() -> execute(request, requestOptions), ASYNC);
    }

    /** The connection pool is shared by every company's client and lives as long as the application. */
    @Override
    public void close() {
    }

    /** An Apache response read by the SDK as it arrives; closing it gives the connection back. */
    private static final class StreamedResponse implements HttpResponse {

        private final ClassicHttpResponse response;
        private final Headers headers;

        private StreamedResponse(ClassicHttpResponse response) {
            this.response = response;
            Headers.Builder builder = Headers.builder();
            for (Header header : response.getHeaders()) {
                builder.put(header.getName(), header.getValue());
            }
            this.headers = builder.build();
        }

        @Override
        public int statusCode() {
            return response.getCode();
        }

        @Override
        public Headers headers() {
            return headers;
        }

        @Override
        public InputStream body() {
            HttpEntity entity = response.getEntity();
            try {
                InputStream content = entity == null ? null : entity.getContent();
                return content == null ? InputStream.nullInputStream() : content;
            } catch (IOException e) {
                throw new OpenAIIoException("the company's LLM answer could not be read", e);
            }
        }

        @Override
        public void close() {
            try {
                response.close();
            } catch (IOException ignored) {
                // The exchange is over either way.
            }
        }
    }
}
