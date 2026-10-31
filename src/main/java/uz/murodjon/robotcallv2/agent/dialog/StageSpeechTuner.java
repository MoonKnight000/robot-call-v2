package uz.murodjon.robotcallv2.agent.dialog;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import uz.murodjon.robotcallv2.agent.rtp.RtpEndpoint;
import uz.murodjon.robotcallv2.agent.stt.SttProperties;
import uz.murodjon.robotcallv2.agent.stt.SttStreamBridge;
import uz.murodjon.robotcallv2.aiagent.domain.entity.AiAgent;
import uz.murodjon.robotcallv2.scenario.domain.entity.StageOverrides;

import java.util.List;

/**
 * Points a cascade call's listening side at what its current stage asks for: the
 * caller's silence that ends a turn ({@code responseWaitMs}), the recognition model
 * ({@code transcriptionMode}) and the boosted keywords. The agent's own values were applied
 * when the pipeline was built ({@code AudioPipelineAssembler}); a stage override replaces
 * them while the stage is active and a stage without one goes back to them.
 *
 * <p>Runs on whichever thread moved the stage. Nothing here blocks: the wait is a field
 * write, and a model or keyword change only marks the recognizer stream for a swap that
 * happens on the call's audio thread ({@link SttStreamBridge#retune}).
 */
final class StageSpeechTuner {

    private static final Logger log = LoggerFactory.getLogger(StageSpeechTuner.class);

    private StageSpeechTuner() {
    }

    static void apply(DialogSession s, SttProperties sttProperties) {
        RtpEndpoint endpoint = s.endpoint();
        SttStreamBridge bridge = endpoint != null ? endpoint.findListener(SttStreamBridge.class) : null;
        if (bridge == null) {
            return; // a simulated or STT-less call has no recognizer to tune
        }
        AiAgent agent = s.agent();
        StageOverrides overrides = SystemPromptFactory.overridesOf(s);

        Integer agentWait = agent != null ? agent.callBehaviour().responseWaitMs() : null;
        Integer wait = overrides != null && overrides.responseWaitMs() != null ? overrides.responseWaitMs() : agentWait;
        // Without a speech gate the recognizer decides end-of-utterance itself: no wait applies.
        bridge.setResponseWaitMs(wait);

        String provider = bridge.providerName();
        String stageModel = overrides != null
                ? sttProperties.findTranscriptionModel(provider, overrides.transcriptionMode()) : null;
        List<String> stageKeywords = overrides != null ? overrides.boostedKeywords() : null;
        if (bridge.retune(provider, stageModel, stageKeywords)) {
            log.debug("[{}] stage {} retunes the recognizer (model={}, keywords={})", s.channelId(), s.state(),
                    stageModel != null ? stageModel : "agent's", stageKeywords != null ? stageKeywords.size() : "agent's");
        }
    }
}
