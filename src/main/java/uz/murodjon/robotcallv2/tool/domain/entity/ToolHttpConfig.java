package uz.murodjon.robotcallv2.tool.domain.entity;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * How a tool's request goes out: the address, the method, what it carries and how long it
 * is waited for.
 *
 * @param bodySchema          a JSON Schema for a body with nested objects and arrays
 *                            (UNIVERSAL-OPERATOR-PLAN B.1); empty when the flat {@code body}
 *                            list describes it. A field carrying {@value Tool#BODY_SOURCE_KEY} is
 *                            filled from the configuration, the call or a secret, not by the model
 * @param responseTimeoutSecs seconds the answer is waited for; unset, 10
 * @param retryAttempts       retries after a failed request, at most {@value Tool#MAX_RETRY_ATTEMPTS}
 * @param auth                the tool's own OAuth client (B.5); null when its headers and
 *                            address carry the authentication
 */
public record ToolHttpConfig(
        String url,
        String method,
        List<ToolKeyValuePair> headers,
        List<ToolParamConfig> body,
        List<ToolParamConfig> queryParams,
        List<ToolParamConfig> pathParams,
        Map<String, Object> bodySchema,
        Integer responseTimeoutSecs,
        int retryAttempts,
        ToolAuth auth
) {
    public ToolHttpConfig {
        method = (method == null || method.isBlank()) ? "POST" : method.toUpperCase();
        headers = headers == null ? List.of() : List.copyOf(headers);
        body = body == null ? List.of() : List.copyOf(body);
        queryParams = queryParams == null ? List.of() : List.copyOf(queryParams);
        pathParams = pathParams == null ? List.of() : List.copyOf(pathParams);
        // Not Map.copyOf: a schema may legitimately hold a null ("default": null).
        bodySchema = bodySchema == null ? Map.of() : Collections.unmodifiableMap(new LinkedHashMap<>(bodySchema));
        responseTimeoutSecs = responseTimeoutSecs == null ? 10 : responseTimeoutSecs;
        retryAttempts = Math.max(0, Math.min(Tool.MAX_RETRY_ATTEMPTS, retryAttempts));
    }
}
