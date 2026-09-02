package uz.murodjon.robotcallv2.engine.domain.entity;

import uz.murodjon.robotcallv2.engine.domain.enums.PipelineMode;

import java.time.Instant;

/**
 * A company's speech-engine choice — domain model of engine_config (§11).
 */
public record EngineConfig(
        long companyId,
        PipelineMode mode,
        String sttProvider,
        String ttsProvider,
        String realtimeProvider,
        Instant createdAt
) {
    public static EngineConfig overrides(PipelineMode mode, String sttProvider, String ttsProvider,
                                         String realtimeProvider) {
        return new EngineConfig(0L, mode, sttProvider, ttsProvider, realtimeProvider, null);
    }
}
