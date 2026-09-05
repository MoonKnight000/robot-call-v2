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
        String pipecatStt,
        String pipecatLlm,
        String pipecatTts,
        Instant createdAt
) {
    public EngineConfig(long companyId, PipelineMode mode, String sttProvider, String ttsProvider,
                        String realtimeProvider, Instant createdAt) {
        this(companyId, mode, sttProvider, ttsProvider, realtimeProvider, null, null, null, createdAt);
    }

    public static EngineConfig overrides(PipelineMode mode, String sttProvider, String ttsProvider,
                                         String realtimeProvider) {
        return new EngineConfig(0L, mode, sttProvider, ttsProvider, realtimeProvider, null, null, null, null);
    }

    public static EngineConfig overrides(PipelineMode mode, String sttProvider, String ttsProvider,
                                         String realtimeProvider, String pipecatStt, String pipecatLlm,
                                         String pipecatTts) {
        return new EngineConfig(0L, mode, sttProvider, ttsProvider, realtimeProvider, pipecatStt, pipecatLlm, pipecatTts, null);
    }
}
