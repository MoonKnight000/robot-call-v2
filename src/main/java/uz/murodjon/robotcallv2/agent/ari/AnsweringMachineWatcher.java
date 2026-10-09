package uz.murodjon.robotcallv2.agent.ari;

import ch.loway.oss.ari4java.ARI;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import uz.murodjon.robotcallv2.agent.audio.AnsweringMachineDetector;
import uz.murodjon.robotcallv2.agent.audio.TalkActivityTracker;
import uz.murodjon.robotcallv2.agent.vad.AmdProperties;
import uz.murodjon.robotcallv2.agent.vad.VadProperties;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;

/**
 * Answering-machine detection on a call no AI pipeline listens to — a progressive call
 * (docs/OPERATOR-DIALER-ANALYTICS-PLAN.md §1.3), joined to its operator without waiting for
 * a verdict. A tap on the client's side feeds the same {@link AnsweringMachineDetector} the
 * AI calls use, with the same {@code voice-agent.vad.amd.*} thresholds; speech is read from
 * the level of each frame rather than from VAD, so the watch needs no model of its own.
 *
 * <p>The tap goes as soon as the detector stops looking: found a machine, or its window
 * closed on a person.
 */
@Component
class AnsweringMachineWatcher {

    private static final Logger log = LoggerFactory.getLogger(AnsweringMachineWatcher.class);

    private final AriClient ariClient;
    private final CallMediaFactory callMediaFactory;
    private final VadProperties vadProperties;
    private final ExecutorService callExecutor;

    private final Map<String, MediaTap> taps = new ConcurrentHashMap<>();

    AnsweringMachineWatcher(AriClient ariClient, CallMediaFactory callMediaFactory, VadProperties vadProperties,
                            ExecutorService callExecutor) {
        this.ariClient = ariClient;
        this.callMediaFactory = callMediaFactory;
        this.vadProperties = vadProperties;
        this.callExecutor = callExecutor;
    }

    /**
     * Starts watching {@code channelId}; {@code onMachine} runs once, off the RTP thread, if a
     * machine answered. False when detection is switched off or the tap could not be opened —
     * the call then goes on unwatched.
     */
    boolean watch(String channelId, Runnable onMachine) {
        AmdProperties amd = vadProperties.amd();
        if (amd == null || !amd.enabled()) {
            return false;
        }
        AnsweringMachineDetector detector = new AnsweringMachineDetector(channelId, CallMediaFactory.SAMPLE_RATE,
                amd.observeMs(), amd.minContinuousSpeechMs(), amd.silenceToleranceMs(),
                () -> callExecutor.execute(onMachine));
        try {
            ARI current = ariClient.requireConnection();
            MediaTap tap = callMediaFactory.openTap(current, channelId, (pcm, length) -> {
                if (detector.isFinished()) {
                    return;
                }
                detector.onVadWindow(TalkActivityTracker.rms(pcm, length) >= TalkActivityTracker.SPEECH_RMS, length);
                if (detector.isFinished()) {
                    callExecutor.execute(() -> stop(channelId));
                }
            });
            taps.put(channelId, tap);
            if (detector.isFinished()) {
                stop(channelId); // finished before the tap was on record, so its own stop found nothing
            }
            return true;
        } catch (Exception e) {
            log.warn("[{}] answering-machine watch not started: {}", channelId, e.getMessage());
            return false;
        }
    }

    /** Closes the watch if it is still open; safe to call more than once. */
    void stop(String channelId) {
        MediaTap tap = taps.remove(channelId);
        if (tap != null) {
            callMediaFactory.releaseTap(ariClient.findConnection(), tap);
        }
    }
}
