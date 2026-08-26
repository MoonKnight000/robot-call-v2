package uz.murodjon.uysotvoice.agent.realtime;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Speech-to-speech engine settings. Bound from {@code voice-agent.realtime.*}
 * (PROJECT.md §12).
 *
 * <p>Unlike {@code voice-agent.stt}/{@code voice-agent.tts}, an empty deployment is
 * normal: a build with no realtime engine wired simply cannot be set to
 * {@code PipelineMode.REALTIME}, and the settings screen hides the mode
 * ({@code EngineOptions.realtime} comes back empty). Nothing fails at startup for it.
 *
 * @param enabled       master switch; when false no company may run on a realtime engine,
 *                      whatever its {@code engine_config} says
 * @param provider      default engine id for a company that chose {@code REALTIME}
 *                      without naming one; blank means there is no default
 * @param factsInPrompt whether the call's facts — the debt amount, the due date — are
 *                      written into the engine's instructions the way the cascade
 *                      pipeline writes them (§4.4).
 *                      <p>False, the default, keeps them out entirely and hands them over
 *                      one at a time through {@code getCallFact}
 *                      ({@code RealtimeFactTools}). The cascade pipeline can afford the
 *                      values in its prompt because it inspects the sentence before it is
 *                      spoken; a realtime engine speaks first and is only audited
 *                      afterwards, so an engine that was never told the amount is the one
 *                      layer that can still prevent rather than detect. The price is a
 *                      tool round trip before a figure, heard as a short pause — set this
 *                      true to trade that safety back for the latency
 * @param geminiLive    Gemini Live settings
 */
@ConfigurationProperties(prefix = "voice-agent.realtime")
public record RealtimeProperties(
        boolean enabled,
        String provider,
        boolean factsInPrompt,
        GeminiLiveProperties geminiLive
) {
}
