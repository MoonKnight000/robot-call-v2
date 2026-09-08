package uz.murodjon.robotcallv2.agent.realtime;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Speech-to-speech engine settings. Bound from {@code voice-agent.realtime.*}
 * (PROJECT.md §12).
 *
 * <p>Unlike {@code voice-agent.stt}/{@code voice-agent.tts}, an empty deployment is
 * normal: a build with no realtime engine wired simply cannot be set to
 * {@code PipelineMode.REALTIME}, and the settings screen hides the mode
 * ({@code EngineOptionsResponse.realtime} comes back empty). Nothing fails at startup for it.
 *
 * @param enabled       master switch; when false no agent may run on a realtime engine,
 *                      whatever the call's agent has as its {@code pipelineMode}
 * @param provider      default engine id for an agent that chose {@code REALTIME}
 *                      without naming one; blank means there is no default
 * @param factsInPrompt whether the call's facts — the debt amount, the due date — are
 *                      written into the engine's instructions the way the cascade
 *                      pipeline writes them (§4.4).
 * @param geminiLive    Gemini Live settings
 * @param openAi        OpenAI Realtime settings
 * @param moshi         Kyutai Moshi settings
 * @param qwenOmni      Alibaba Cloud Qwen-Omni settings
 * @param pipecat       Pipecat Cloud & Claude Realtime settings
 */
@ConfigurationProperties(prefix = "voice-agent.realtime")
public record RealtimeProperties(
        boolean enabled,
        String provider,
        boolean factsInPrompt,
        GeminiLiveProperties geminiLive,
        OpenAiRealtimeProperties openAi,
        MoshiRealtimeProperties moshi,
        QwenOmniRealtimeProperties qwenOmni,
        PipecatRealtimeProperties pipecat
) {
}
