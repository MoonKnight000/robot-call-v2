package uz.murodjon.robotcallv2.tool.domain.entity;

import uz.murodjon.robotcallv2.tool.domain.enums.ToolEffect;

import java.time.Instant;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * A company's REST endpoint the agent may call during a conversation.
 *
 * @param bodySchema           a JSON Schema for a body with nested objects and arrays
 *                             (UNIVERSAL-OPERATOR-PLAN B.1); empty when the flat {@code apiBody}
 *                             list describes it. A field carrying {@value #BODY_SOURCE_KEY} is
 *                             filled from the configuration, the call or a secret, not by the model
 * @param effect               what calling it does (B.3); unset, a GET reads and anything else writes
 * @param requiresConfirmation whether a call is read back to the caller and sent only after
 *                             their yes; unset, on for a {@code WRITE} tool
 * @param readbackTemplate     the sentence that reads a call back, {@code {{name}}} per argument;
 *                             null leaves the wording to the model
 * @param auth                 the tool's own OAuth client (B.5); null when its headers and
 *                             address carry the authentication
 */
public record Tool(
        Long id,
        Long companyId,
        String name,
        String description,
        String apiUrl,
        String apiMethod,
        List<ToolKeyValuePair> apiHeaders,
        List<ToolParamConfig> apiBody,
        List<ToolParamConfig> apiQueryParams,
        List<ToolParamConfig> apiPathParams,
        Integer responseTimeoutSecs,
        List<ToolKeyValuePair> dynamicVariables,
        boolean disableInterruptions,
        boolean forcePreToolSpeech,
        String preToolSpeech,
        Instant createdAt,
        Instant updatedAt,
        Map<String, String> responseMapping,
        int retryAttempts,
        int maxCallsPerCall,
        Map<String, Object> bodySchema,
        ToolEffect effect,
        Boolean requiresConfirmation,
        String readbackTemplate,
        ToolAuth auth
) {
    /** Retries past this are a tool that is down, not one having a bad moment. */
    public static final int MAX_RETRY_ATTEMPTS = 3;
    /** What a new tool gets: a lookup, a correction and a re-check. */
    public static final int DEFAULT_MAX_CALLS_PER_CALL = 3;

    /** bodySchema keyword naming where a field's value comes from: {@code static}, {@code variable} or {@code secret}. */
    public static final String BODY_SOURCE_KEY = "x-source";
    /** bodySchema keyword holding the value, the variable name or the secret key; unset, the field's own name. */
    public static final String BODY_VALUE_KEY = "x-value";

    public Tool {
        apiMethod = (apiMethod == null || apiMethod.isBlank()) ? "POST" : apiMethod.toUpperCase();
        apiHeaders = apiHeaders == null ? List.of() : List.copyOf(apiHeaders);
        apiBody = apiBody == null ? List.of() : List.copyOf(apiBody);
        apiQueryParams = apiQueryParams == null ? List.of() : List.copyOf(apiQueryParams);
        apiPathParams = apiPathParams == null ? List.of() : List.copyOf(apiPathParams);
        dynamicVariables = dynamicVariables == null ? List.of() : List.copyOf(dynamicVariables);
        responseTimeoutSecs = responseTimeoutSecs == null ? 10 : responseTimeoutSecs;
        responseMapping = responseMapping == null ? Map.of() : Map.copyOf(responseMapping);
        retryAttempts = Math.max(0, Math.min(MAX_RETRY_ATTEMPTS, retryAttempts));
        maxCallsPerCall = Math.max(0, maxCallsPerCall);
        // Not Map.copyOf: a schema may legitimately hold a null ("default": null).
        bodySchema = bodySchema == null ? Map.of() : Collections.unmodifiableMap(new LinkedHashMap<>(bodySchema));
        effect = effect != null ? effect : ("GET".equals(apiMethod) ? ToolEffect.READ : ToolEffect.WRITE);
        requiresConfirmation = requiresConfirmation != null ? requiresConfirmation : effect == ToolEffect.WRITE;
    }
}
