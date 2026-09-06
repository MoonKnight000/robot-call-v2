package uz.murodjon.robotcallv2.agent.realtime;

import org.springframework.ai.tool.ToolCallback;

import java.util.List;

/**
 * What one call tells a {@link RealtimeProvider} about itself when the session opens —
 * the realtime equivalent of the language, system prompt and tool list {@code
 * DialogEngine} assembles per turn in the cascade pipeline. Resolved once: a realtime
 * engine is instructed at connect time, not on every turn.
 *
 * @param channelId    the Asterisk channel this conversation belongs to — for logging
 *                     and MDC, so a provider's own logs join the rest of the call's
 * @param language     BCP-47 language the conversation is held in (e.g. {@code uz-UZ})
 * @param systemPrompt the instructions the engine runs under, built the same way the
 *                     cascade pipeline builds them ({@code SystemPromptFactory})
 * @param voice        provider-side voice name, or {@code null} for the engine's default
 * @param tools        the tools the engine may call, reused as-is from the cascade
 *                     pipeline.
 * @param pipecatStt   per-company Pipecat STT sub-engine (e.g. deepgram, soniox, yandex)
 * @param pipecatLlm   per-company Pipecat LLM sub-engine (e.g. claude-3-5-haiku, gemini-2.0-flash)
 * @param pipecatTts   per-company Pipecat TTS sub-engine (e.g. cartesia, elevenlabs, yandex)
 * @param model        the agent's own model for this call ({@code AiAgent.llmModel}),
 *                     or {@code null} to leave the provider on the model it was deployed
 *                     with. Providers read it through {@link #modelOr}, so a company that
 *                     configures nothing per scenario behaves exactly as before
 */
public record RealtimeCallConfig(
        String channelId,
        String language,
        String systemPrompt,
        String voice,
        List<ToolCallback> tools,
        String pipecatStt,
        String pipecatLlm,
        String pipecatTts,
        String model
) {
    public RealtimeCallConfig(String channelId, String language, String systemPrompt,
                              String voice, List<ToolCallback> tools) {
        this(channelId, language, systemPrompt, voice, tools, null, null, null, null);
    }

    /**
     * The model this call should run on.
     *
     * <p>One rule in one place rather than the same three-line ternary in five providers —
     * and the rule is the same as everywhere else in this project: what was chosen closer to
     * the call wins, and what was not chosen falls through.
     *
     * @param fallback what the provider would have used on its own — its configured model,
     *                 or for Pipecat the company's LLM sub-engine
     */
    public String modelOr(String fallback) {
        return model != null && !model.isBlank() ? model.trim() : fallback;
    }
}
