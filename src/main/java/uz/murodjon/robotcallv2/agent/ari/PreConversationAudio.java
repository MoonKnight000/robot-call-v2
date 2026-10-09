package uz.murodjon.robotcallv2.agent.ari;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Component;
import uz.murodjon.robotcallv2.agent.flow.FlowPrompter;
import uz.murodjon.robotcallv2.agent.rtp.RtpEndpoint;
import uz.murodjon.robotcallv2.aiagent.domain.entity.AiAgent;

/**
 * The agent's pre-conversation audio ({@code AiAgentCallBehaviour.preConversationAudioFileId}):
 * an uploaded WAV played the moment the call is up, before the dialog starts — a notice
 * recorded in a person's voice, a company jingle. The dialog's opening line waits for it,
 * so the two never talk over each other.
 */
@Component
class PreConversationAudio {

    private static final Logger log = LoggerFactory.getLogger(PreConversationAudio.class);

    /** Beyond the audio's own length, how long playback may lag before the dialog starts anyway. */
    private static final long PLAYBACK_SLACK_MS = 1500;

    /** Lazily: the IVR prompter sits on the far side of the PBX wiring this service is part of. */
    private final ObjectProvider<FlowPrompter> flowPrompterProvider;
    private final CallMediaFactory callMediaFactory;

    PreConversationAudio(ObjectProvider<FlowPrompter> flowPrompterProvider, CallMediaFactory callMediaFactory) {
        this.flowPrompterProvider = flowPrompterProvider;
        this.callMediaFactory = callMediaFactory;
    }

    /**
     * Plays the agent's audio on {@code endpoint} and returns once it has been heard. A
     * file that cannot be read — gone, another company's, not a WAV — is skipped: the call
     * goes on without it rather than not at all.
     */
    void play(String channelId, long companyId, AiAgent agent, RtpEndpoint endpoint) {
        Long fileId = agent != null ? agent.callBehaviour().preConversationAudioFileId() : null;
        FlowPrompter prompter = fileId != null ? flowPrompterProvider.getIfAvailable() : null;
        if (prompter == null) {
            return;
        }
        // Read with the call's own company: the storage tenant check is what keeps one
        // company's agent from playing another's file.
        short[] pcm = prompter.recording(companyId, fileId);
        if (pcm.length == 0) {
            log.warn("[{}] pre-conversation audio {} could not be played — starting without it", channelId, fileId);
            return;
        }
        endpoint.playPcm(pcm);
        callMediaFactory.awaitSilence(endpoint, pcm.length * 1000L / CallMediaFactory.SAMPLE_RATE + PLAYBACK_SLACK_MS);
        log.info("[{}] pre-conversation audio {} played", channelId, fileId);
    }
}
