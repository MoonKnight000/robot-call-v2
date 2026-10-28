package uz.murodjon.robotcallv2.tool.domain.entity;

import uz.murodjon.robotcallv2.tool.domain.enums.ToolEffect;

import java.time.Instant;
import java.util.List;
import java.util.Map;

/**
 * A company's REST endpoint the agent may call during a conversation.
 *
 * @param http                 how the request goes out; never null
 * @param speech               what the caller hears while it runs; never null
 * @param effect               what calling it does (B.3); unset, a GET reads and anything else writes
 * @param requiresConfirmation whether a call is read back to the caller and sent only after
 *                             their yes; unset, on for a {@code WRITE} tool
 * @param readbackTemplate     the sentence that reads a call back, {@code {{name}}} per argument;
 *                             null leaves the wording to the model
 */
public record Tool(
        Long id,
        Long companyId,
        String name,
        String description,
        ToolHttpConfig http,
        List<ToolKeyValuePair> dynamicVariables,
        ToolSpeech speech,
        Map<String, String> responseMapping,
        int maxCallsPerCall,
        ToolEffect effect,
        Boolean requiresConfirmation,
        String readbackTemplate,
        Instant createdAt,
        Instant updatedAt
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
        http = http != null ? http : new ToolHttpConfig(null, null, null, null, null, null, null, null, 0, null);
        speech = speech != null ? speech : new ToolSpeech(false, false, null);
        dynamicVariables = dynamicVariables == null ? List.of() : List.copyOf(dynamicVariables);
        responseMapping = responseMapping == null ? Map.of() : Map.copyOf(responseMapping);
        maxCallsPerCall = Math.max(0, maxCallsPerCall);
        effect = effect != null ? effect : ("GET".equals(http.method()) ? ToolEffect.READ : ToolEffect.WRITE);
        requiresConfirmation = requiresConfirmation != null ? requiresConfirmation : effect == ToolEffect.WRITE;
    }
}
