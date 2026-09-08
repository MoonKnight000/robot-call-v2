package uz.murodjon.robotcallv2.aiagent.domain.entity;

import uz.murodjon.robotcallv2.aiagent.domain.enums.PipelineMode;

/**
 * Which engines run the call.
 *
 * <p>{@code mode} decides which of the rest apply: a {@link PipelineMode#CASCADE} call is
 * assembled here from a recogniser and a synthesiser, a realtime call hands the audio to
 * one provider that does both, and a pipecat call hands it to an external service
 * configured with its own three sub-engines. They are one group because reading any of
 * them without the mode tells you nothing — a {@code sttProvider} on a realtime agent is
 * a leftover, not a setting.
 *
 * <p>A null provider or model means "the configured default for this deployment"; nothing
 * here is defaulted to a name, because the names belong to configuration, not to an agent.
 */
public record AiAgentSpeechEngine(
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
    public AiAgentSpeechEngine {
        if (mode == null) {
            mode = PipelineMode.CASCADE;
        }
    }
}
