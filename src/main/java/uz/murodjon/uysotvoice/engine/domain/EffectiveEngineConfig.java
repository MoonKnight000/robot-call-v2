package uz.murodjon.uysotvoice.engine.domain;

import uz.murodjon.uysotvoice.engine.enums.PipelineMode;

/**
 * A company's {@link EngineConfig} merged over the process-wide defaults — which engine
 * one call actually runs on. Resolved once per call and reused for its whole duration,
 * so a setting changed mid-call never splits a conversation across two engines.
 *
 * <p>Unlike {@link EngineConfig}, the provider fields are never null for the mode in
 * use: a company that overrode nothing still gets the configured process default here,
 * so call-path code never has to repeat the fallback. The field belonging to the
 * <em>other</em> mode is still filled in — it costs nothing and keeps the record a
 * plain resolved view rather than a mode-shaped union.
 */
public record EffectiveEngineConfig(
        PipelineMode mode,
        String sttProvider,
        String ttsProvider,
        String realtimeProvider
) {
}
