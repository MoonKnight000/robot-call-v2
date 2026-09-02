package uz.murodjon.robotcallv2.engine.application.dto;

import uz.murodjon.robotcallv2.engine.domain.enums.PipelineMode;

/**
 * PUT /api/settings/engine body (§11).
 */
public record UpdateEngineConfigRequest(
        PipelineMode mode,
        String sttProvider,
        String ttsProvider,
        String realtimeProvider
) {
}
