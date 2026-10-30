package uz.murodjon.robotcallv2.agent.ari;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import uz.murodjon.robotcallv2.agent.audio.VoicemailBeepDetector;
import uz.murodjon.robotcallv2.agent.rtp.RtpEndpoint;
import uz.murodjon.robotcallv2.agent.session.CallMdc;
import uz.murodjon.robotcallv2.agent.tts.TtsRouter;
import uz.murodjon.robotcallv2.agent.telephony.TelephonyGatewayResolver;
import uz.murodjon.robotcallv2.agent.vad.AmdProperties;
import uz.murodjon.robotcallv2.agent.vad.VadProperties;
import uz.murodjon.robotcallv2.aiagent.domain.entity.AiAgent;

import java.util.concurrent.ExecutorService;

/**
 * Leaves an agent's voicemail message on a <em>realtime</em> call (VOICE-PIPELINE-PLAN
 * Bosqich 2, the part left over). The cascade pipeline has {@code VoicemailDrop}; a realtime
 * call has no voice of ours in it — the engine speaks — so here the engine is closed first,
 * and the message is synthesized by our own TTS in the company's default voice, after the
 * beep ({@link VoicemailBeepDetector}), and the line hung up when it has been said.
 *
 * <p>The caller ({@code AudioPipelineAssembler}) has already recorded the call as
 * {@code VOICEMAIL}; the engine being gone, teardown reads it from
 * {@link CallSessionRegistry#rememberDisposition}.
 */
@Component
class RealtimeVoicemailDrop {

    private static final Logger log = LoggerFactory.getLogger(RealtimeVoicemailDrop.class);

    private static final int SAMPLE_RATE = 8000;
    /** Slack over the message's own length before the line is hung up regardless. */
    private static final long PLAYBACK_SLACK_MS = 2000;

    private final TtsRouter ttsRouter;
    private final VadProperties vadProperties;
    private final TelephonyGatewayResolver telephonyGatewayResolver;
    private final CallMediaFactory callMediaFactory;
    private final ExecutorService callExecutor;

    RealtimeVoicemailDrop(TtsRouter ttsRouter, VadProperties vadProperties,
                          TelephonyGatewayResolver telephonyGatewayResolver,
                          CallMediaFactory callMediaFactory, ExecutorService callExecutor) {
        this.ttsRouter = ttsRouter;
        this.vadProperties = vadProperties;
        this.telephonyGatewayResolver = telephonyGatewayResolver;
        this.callMediaFactory = callMediaFactory;
        this.callExecutor = callExecutor;
    }

    /** Waits for the beep on {@code endpoint}, then speaks {@code message} and hangs up. */
    void start(String channelId, RtpEndpoint endpoint, AiAgent agent, String message) {
        endpoint.flushPlayback();
        AmdProperties amd = vadProperties.amd();
        int silenceMs = amd != null ? amd.beepSilenceMs() : AmdProperties.DEFAULT_BEEP_SILENCE_MS;
        int maxWaitMs = amd != null ? amd.beepMaxWaitMs() : AmdProperties.DEFAULT_BEEP_MAX_WAIT_MS;
        log.info("[{}] voicemail on a realtime call — waiting for the beep before leaving the message", channelId);
        endpoint.addListener(new VoicemailBeepDetector(channelId, SAMPLE_RATE, silenceMs, maxWaitMs,
                () -> callExecutor.execute(() -> CallMdc.with(channelId,
                        () -> speakAndHangUp(channelId, endpoint, agent, message)))));
    }

    private void speakAndHangUp(String channelId, RtpEndpoint endpoint, AiAgent agent, String message) {
        try {
            // The agent's voice belongs to the realtime engine; an id the TTS catalog does
            // not know falls back to the default provider's voice for the language.
            String language = agent.language();
            short[] pcm = ttsRouter.synthesize(message, language, agent.voiceFor(language));
            endpoint.enqueuePcm(pcm);
            callMediaFactory.awaitSilence(endpoint, pcm.length * 1000L / SAMPLE_RATE + PLAYBACK_SLACK_MS);
            log.info("[{}] voicemail message left", channelId);
        } catch (Exception e) {
            log.warn("[{}] voicemail message could not be left: {}", channelId, e.getMessage());
        } finally {
            telephonyGatewayResolver.findByCallId(channelId).hangup(channelId);
        }
    }
}
