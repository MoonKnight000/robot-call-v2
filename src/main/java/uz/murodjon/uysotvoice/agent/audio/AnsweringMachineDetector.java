package uz.murodjon.uysotvoice.agent.audio;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Detects that an answering machine, not a person, picked up (PROJECT.md §8.6).
 *
 * <p>Asterisk ships {@code AMD()}, and the spec calls it unreliable; the MVP heuristic it
 * proposes instead is what this implements. A person answering says something short —
 * "alo?", "eshitaman" — and then waits. A machine plays an uninterrupted greeting several
 * seconds long. So: within the first few seconds of audio, one continuous run of speech
 * longer than a human answer means a machine.
 *
 * <p>Left undetected, the agent holds a full conversation with a voicemail beep: it pays
 * for STT on the greeting, LLM turns on nonsense transcripts, and TTS for a reply nobody
 * hears, then records a disposition that has nothing to do with the caller. Catching it in
 * the first seconds turns that into one cheap {@code VOICEMAIL} and a retry.
 *
 * <p>Fed the same VAD window scores as barge-in, so it costs no extra inference. Runs on
 * the RTP consumer thread and holds no locks.
 *
 * <p>The thresholds are a trade: too short and a talkative human ("Assalomu alaykum,
 * eshitaman, kim gaplashadi?") is cut off mid-sentence, which is far worse than paying for
 * one wasted voicemail. They default conservatively for that reason — tune them down only
 * against real recordings.
 */
public class AnsweringMachineDetector {

    private static final Logger log = LoggerFactory.getLogger(AnsweringMachineDetector.class);

    private final String channelId;
    private final int sampleRate;
    private final int observeSamples;
    private final int minContinuousSamples;
    private final int silenceToleranceSamples;
    private final Runnable onDetected;

    private int elapsedSamples;
    private int speechRunSamples;
    private int silenceRunSamples;
    private boolean finished;

    /**
     * @param sampleRate            audio rate of the scored windows
     * @param observeMs             how long after the call connects to keep watching. Past
     *                              this the conversation has started and a long utterance
     *                              is just someone talking
     * @param minContinuousSpeechMs continuous speech that means "recording, not person"
     * @param silenceToleranceMs    silence allowed inside one run before it is treated as
     *                              a real pause. Speech dips below the VAD threshold on
     *                              plosives, and without this every consonant would reset
     *                              the run and nothing would ever be detected
     * @param onDetected            invoked once, on the RTP thread, when a machine is found
     */
    public AnsweringMachineDetector(String channelId, int sampleRate, int observeMs,
                                    int minContinuousSpeechMs, int silenceToleranceMs,
                                    Runnable onDetected) {
        this.channelId = channelId;
        this.sampleRate = sampleRate;
        this.observeSamples = Math.max(1, observeMs) * sampleRate / 1000;
        this.minContinuousSamples = Math.max(1, minContinuousSpeechMs) * sampleRate / 1000;
        this.silenceToleranceSamples = Math.max(0, silenceToleranceMs) * sampleRate / 1000;
        this.onDetected = onDetected;
    }

    /**
     * One scored VAD window.
     *
     * @param speech  whether the window scored above the VAD speech threshold
     * @param samples how many samples the window covered
     */
    public void onVadWindow(boolean speech, int samples) {
        if (finished) {
            return;
        }
        elapsedSamples += samples;
        if (speech) {
            silenceRunSamples = 0;
            speechRunSamples += samples;
            if (speechRunSamples >= minContinuousSamples) {
                finished = true;
                log.info("[{}] answering machine: {} ms of continuous speech within the first {} ms",
                        channelId, msOf(speechRunSamples), msOf(observeSamples));
                try {
                    onDetected.run();
                } catch (Exception e) {
                    log.warn("[{}] answering-machine handler failed: {}", channelId, e.getMessage());
                }
                return;
            }
        } else {
            silenceRunSamples += samples;
            if (silenceRunSamples > silenceToleranceSamples) {
                // A real pause — whoever answered stopped to listen, which is what a
                // person does.
                speechRunSamples = 0;
            }
        }
        if (elapsedSamples >= observeSamples) {
            finished = true;
            log.debug("[{}] answering-machine window closed without a detection", channelId);
        }
    }

    /** Whether this detector has stopped looking (detected, or window expired). */
    public boolean isFinished() {
        return finished;
    }

    private int msOf(int samples) {
        return samples * 1000 / Math.max(1, sampleRate);
    }
}
