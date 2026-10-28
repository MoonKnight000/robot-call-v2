package uz.murodjon.robotcallv2.aiagent.application.dto;

import uz.murodjon.robotcallv2.aiagent.domain.entity.AiAgentSpeechEngine;
import uz.murodjon.robotcallv2.shared.dialog.PipelineMode;

/**
 * {@link AiAgentSpeechEngine} as the agent endpoints read and write it. On a write a null
 * field keeps what the agent has (or the create default).
 */
public record AgentSpeechEngineDto(
        PipelineMode mode,
        String realtimeProvider,
        String pipecatStt,
        String pipecatLlm,
        String pipecatTts,
        String sttProvider,
        String sttModel,
        String ttsProvider,
        String ttsModel
) {

    public static AgentSpeechEngineDto of(AiAgentSpeechEngine engine) {
        return new AgentSpeechEngineDto(engine.mode(), engine.realtimeProvider(), engine.pipecatStt(),
                engine.pipecatLlm(), engine.pipecatTts(), engine.sttProvider(), engine.sttModel(),
                engine.ttsProvider(), engine.ttsModel());
    }
}
