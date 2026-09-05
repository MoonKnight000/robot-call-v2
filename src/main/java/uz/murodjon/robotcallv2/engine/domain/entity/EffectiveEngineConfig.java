package uz.murodjon.robotcallv2.engine.domain.entity;

import uz.murodjon.robotcallv2.engine.domain.enums.PipelineMode;

/**
 * A company's EngineConfig merged over the process-wide defaults.
 */
public record EffectiveEngineConfig(
        PipelineMode mode,
        String sttProvider,
        String ttsProvider,
        String realtimeProvider,
        String pipecatStt,
        String pipecatLlm,
        String pipecatTts
) {
    public EffectiveEngineConfig(PipelineMode mode, String sttProvider, String ttsProvider, String realtimeProvider) {
        this(mode, sttProvider, ttsProvider, realtimeProvider, null, null, null);
    }
}
