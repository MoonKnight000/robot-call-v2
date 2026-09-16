package uz.murodjon.robotcallv2.tool.infrastructure.adapter;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;
import org.yaml.snakeyaml.LoaderOptions;
import org.yaml.snakeyaml.Yaml;
import org.yaml.snakeyaml.constructor.SafeConstructor;
import uz.murodjon.robotcallv2.shared.exception.ErrorCode;
import uz.murodjon.robotcallv2.shared.exception.ExternalServiceException;
import uz.murodjon.robotcallv2.shared.http.PublicHttpClient;
import uz.murodjon.robotcallv2.shared.http.PublicHttpRequest;
import uz.murodjon.robotcallv2.shared.http.PublicHttpResponse;
import uz.murodjon.robotcallv2.shared.util.PublicUrlGuard;
import uz.murodjon.robotcallv2.tool.application.port.output.OpenApiDocumentPort;

import java.io.IOException;
import java.net.URI;
import java.time.Duration;
import java.util.Map;

/**
 * Reads an OpenAPI document as JSON or YAML. YAML goes through SnakeYAML's safe constructor —
 * no object tags, and its alias limit stands against a document built to expand in memory.
 */
@Component
public class OpenApiDocumentReaderAdapter implements OpenApiDocumentPort {

    /** A large public API (Stripe's) is a few megabytes; past this it is not a document. */
    private static final int MAX_DOCUMENT_BYTES = 10 * 1024 * 1024;
    private static final Duration FETCH_TIMEOUT = Duration.ofSeconds(20);
    private static final ObjectMapper JSON = new ObjectMapper();
    private static final TypeReference<Map<String, Object>> DOCUMENT_TYPE = new TypeReference<>() {};

    private final PublicHttpClient httpClient = PublicHttpClient.create(Duration.ofSeconds(10), FETCH_TIMEOUT);

    @Override
    public Map<String, Object> fetchDocument(String url) {
        URI uri = PublicUrlGuard.parsePublic(url);
        if (uri == null) {
            throw new ExternalServiceException(ErrorCode.OPENAPI_FETCH_FAILED, "openapi", url, "not a public address");
        }
        try {
            PublicHttpResponse response = httpClient.send(PublicHttpRequest.create("GET", uri)
                    .addHeader("Accept", "application/json, application/yaml, text/yaml, */*")
                    .setTimeout(FETCH_TIMEOUT)
                    .setMaxResponseBytes(MAX_DOCUMENT_BYTES));
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                throw new ExternalServiceException(ErrorCode.OPENAPI_FETCH_FAILED, "openapi", url,
                        "HTTP " + response.statusCode());
            }
            if (response.truncated()) {
                throw new ExternalServiceException(ErrorCode.OPENAPI_FETCH_FAILED, "openapi", url,
                        "the document is larger than " + MAX_DOCUMENT_BYTES / (1024 * 1024) + " MB");
            }
            return parseDocument(response.readBodyText());
        } catch (IOException e) {
            throw new ExternalServiceException(ErrorCode.OPENAPI_FETCH_FAILED, "openapi", e, url, e.getMessage());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new ExternalServiceException(ErrorCode.OPENAPI_FETCH_FAILED, "openapi", e, url, "interrupted");
        }
    }

    @Override
    public Map<String, Object> parseDocument(String text) {
        if (text == null || text.isBlank()) {
            return null;
        }
        String trimmed = text.strip();
        try {
            if (trimmed.startsWith("{")) {
                return JSON.readValue(trimmed, DOCUMENT_TYPE);
            }
            LoaderOptions options = new LoaderOptions();
            options.setCodePointLimit(MAX_DOCUMENT_BYTES);
            Object loaded = new Yaml(new SafeConstructor(options)).load(trimmed);
            // Through Jackson once, so a YAML key that is a number (a response code) becomes
            // text like it is in JSON, and the two formats give the same tree.
            return loaded instanceof Map<?, ?> ? JSON.convertValue(loaded, DOCUMENT_TYPE) : null;
        } catch (Exception e) {
            return null;
        }
    }
}
