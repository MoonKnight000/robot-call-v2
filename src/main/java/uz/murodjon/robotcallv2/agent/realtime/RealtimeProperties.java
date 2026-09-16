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
 *                      pipeline writes them (§4.4). The deployment default only: an agent
 *                      that sets {@code realtimeFactsDelivery} decides for itself
 * @param normalizeOutputLevel bring the engine's speech to one steady level before it is
 *                      encoded for the phone line ({@code SpeechLevelNormalizer}). Engines
 *                      differ by several dB from each other and within one call, and the
 *                      loud end reaches µ-law clipped
 * @param echoGateRatio caller audio quieter than this share of the bot's own recent output
 *                      level is treated as the bot's voice coming back down the line and
 *                      attenuated before the engine hears it — otherwise an engine on a line
 *                      without echo cancellation interrupts itself. 0.15 is about −16 dB,
 *                      under any caller actually talking over the bot and over most line
 *                      echo; 0 turns the gate off
 * @param echoMinBotLevel bot output level (RMS) below which the gate stays open — the bot
 *                      is not really saying anything, so there is nothing to echo
 * @param toolTimeoutSeconds the longest a tool call the engine made may run before the
 *                      engine is told it failed and asked to carry on. A tool that hangs
 *                      otherwise leaves the caller in silence with no cap at all — the
 *                      no-input watchdog stands down while a tool is in flight
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
        boolean normalizeOutputLevel,
        double echoGateRatio,
        double echoMinBotLevel,
        int toolTimeoutSeconds,
        GeminiLiveProperties geminiLive,
        OpenAiRealtimeProperties openAi,
        MoshiRealtimeProperties moshi,
        QwenOmniRealtimeProperties qwenOmni,
        PipecatRealtimeProperties pipecat
) {
}
