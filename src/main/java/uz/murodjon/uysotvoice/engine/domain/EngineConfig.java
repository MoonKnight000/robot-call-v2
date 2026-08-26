package uz.murodjon.uysotvoice.engine.domain;

import uz.murodjon.uysotvoice.engine.enums.PipelineMode;

import java.time.Instant;

/**
 * A company's speech-engine choice — the domain model of {@code engine_config}, passed
 * between service and repository and returned by {@code GET/PUT /api/settings/engine}
 * (§11).
 *
 * <p>Every provider field is nullable: null means "use the process default"
 * ({@code voice-agent.stt.provider} / {@code voice-agent.tts.provider} /
 * {@code voice-agent.realtime.provider}), not "none". Which fields are read depends on
 * {@link #mode}: {@link PipelineMode#CASCADE} uses {@code sttProvider}/{@code
 * ttsProvider}, {@link PipelineMode#REALTIME} uses {@code realtimeProvider} and neither
 * of the other two — a realtime engine is not an STT and a TTS bolted together.
 *
 * <p>{@code companyId} and {@code createdAt} are repository-owned: ignored and
 * re-stamped on the way in, always filled on the way out. Use {@link #overrides} to
 * build a value that is only meant to be written.
 */
public record EngineConfig(
        long companyId,
        PipelineMode mode,
        String sttProvider,
        String ttsProvider,
        String realtimeProvider,
        Instant createdAt
) {

    /**
     * The choice alone, without the repository-owned {@code companyId}/{@code createdAt}
     * — what a {@code PUT} carries. The repository stamps both on save.
     */
    public static EngineConfig overrides(PipelineMode mode, String sttProvider, String ttsProvider,
                                         String realtimeProvider) {
        return new EngineConfig(0L, mode, sttProvider, ttsProvider, realtimeProvider, null);
    }
}
