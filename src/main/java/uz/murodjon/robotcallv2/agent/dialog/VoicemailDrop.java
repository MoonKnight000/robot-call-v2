package uz.murodjon.robotcallv2.agent.dialog;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import uz.murodjon.robotcallv2.agent.audio.VoicemailBeepDetector;
import uz.murodjon.robotcallv2.agent.session.CallMdc;
import uz.murodjon.robotcallv2.agent.vad.AmdProperties;
import uz.murodjon.robotcallv2.agent.vad.VadProperties;
import uz.murodjon.robotcallv2.shared.dialog.Disposition;

/**
 * Leaving the agent's message on a voicemail ({@code VoicemailAction.LEAVE_MESSAGE}),
 * however the voicemail was found — the audio heuristic or a phrase in the transcript.
 *
 * <p>The bot stops at once, then waits for the box to start recording
 * ({@link VoicemailBeepDetector}) before it speaks: spoken straight away, the message
 * went out over the greeting and the recording kept only its tail.
 *
 * <p>The session is ended and the hang-up claimed before the wait, so a turn still in
 * flight neither answers the greeting nor hangs up under the message: it finds the call
 * ended, and {@code finishWhenSpoken} finds the hang-up already taken.
 */
@Component
class VoicemailDrop {

    private static final Logger log = LoggerFactory.getLogger(VoicemailDrop.class);

    /** The rate of the PCM every {@code AudioListener} is fed. */
    private static final int SAMPLE_RATE = 8000;

    private final VadProperties vadProperties;
    private final SpeechOutput speech;
    private final DialogTranscript transcript;
    private final DialogPolicy dialogPolicy;
    private final DialogExecutors executors;

    VoicemailDrop(VadProperties vadProperties, SpeechOutput speech, DialogTranscript transcript,
                  DialogPolicy dialogPolicy, DialogExecutors executors) {
        this.vadProperties = vadProperties;
        this.speech = speech;
        this.transcript = transcript;
        this.dialogPolicy = dialogPolicy;
        this.executors = executors;
    }

    /** @return false when the call was already over or already hanging up — nothing was left */
    boolean start(DialogSession s, String message) {
        if (s.isLineGone() || !s.beginClosing()) {
            return false;
        }
        s.end(Disposition.VOICEMAIL);
        s.setCancelled(true);
        s.endpoint().flushPlayback();
        AmdProperties amd = vadProperties.amd();
        int silenceMs = amd != null ? amd.beepSilenceMs() : AmdProperties.DEFAULT_BEEP_SILENCE_MS;
        int maxWaitMs = amd != null ? amd.beepMaxWaitMs() : AmdProperties.DEFAULT_BEEP_MAX_WAIT_MS;
        log.info("[{}] voicemail — waiting for the beep before leaving the message", s.channelId());
        s.endpoint().addListener(new VoicemailBeepDetector(s.channelId(), SAMPLE_RATE, silenceMs, maxWaitMs,
                () -> executors.submit(() -> CallMdc.with(s.channelId(), () -> speak(s, message)))));
        return true;
    }

    private void speak(DialogSession s, String message) {
        if (s.isLineGone()) {
            return;
        }
        // Set by start() to silence the bot; speech refuses to say anything while it is.
        s.setCancelled(false);
        transcript.recordAgentLine(s, message);
        speech.speakChunk(s, message);
        dialogPolicy.awaitPlaybackDrained(s.endpoint());
        Runnable hangup = s.hangup();
        if (hangup != null) {
            hangup.run();
        }
    }
}
