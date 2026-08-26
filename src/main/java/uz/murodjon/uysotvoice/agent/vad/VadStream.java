package uz.murodjon.uysotvoice.agent.vad;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import uz.murodjon.uysotvoice.agent.audio.AnsweringMachineDetector;
import uz.murodjon.uysotvoice.agent.audio.AudioListener;
import uz.murodjon.uysotvoice.agent.audio.SpeechGate;

import java.util.function.BooleanSupplier;

/**
 * Per-call barge-in detector (PROJECT.md §7.2). Buffers a call's decoded 8 kHz PCM
 * into fixed windows, scores each with {@link SileroVad}, and fires the barge-in
 * callback once continuous speech exceeds {@code minSpeechMs} — so short "aha"/"ha"
 * back-channels are ignored. Re-arms after a silence gap for the next utterance.
 *
 * <p>Only an interruption that actually <em>landed</em> disarms the detector. Most speech
 * on a call interrupts nothing — the bot is not talking, and the caller is simply taking
 * their turn — and treating that as the call's one barge-in used to leave the detector
 * waiting for a silence gap that a talkative caller never gives it. The bot would then
 * start replying over them and could not be stopped for the rest of the utterance. A
 * callback that reports it did nothing costs only another {@code minSpeechMs} of the same
 * speech run before the next attempt.
 *
 * <p>The same scores also drive an optional {@link SpeechGate}, which is what keeps
 * silence off the (per-second billed) STT stream. Both wait for a run of continuous
 * speech before acting — a cough must not silence the bot, and a voice across the room
 * must not be transcribed as a caller turn — but they wait independently, and the gate
 * can afford a shorter run because its pre-roll buffer replays whatever it spent
 * confirming.
 *
 * <p>Runs on the RTP consumer thread; Silero inference on a 32ms window is cheap.
 */
public class VadStream implements AudioListener {

    private static final Logger log = LoggerFactory.getLogger(VadStream.class);

    private final SileroVad model;
    private final String channelId;
    private final float threshold;
    private final int minSpeechWindows;
    private final int silenceResetWindows;
    private final BooleanSupplier onBargeIn;
    /** Fed every scored window; null when STT gating is off. */
    private final SpeechGate gate;
    /** Fed every scored window; null when answering-machine detection is off. */
    private final AnsweringMachineDetector amd;

    private final float[] window;
    private final float[][][] state;
    private int filled;
    private int speechWindows;
    private int silenceWindows;
    private boolean armed = true;
    private boolean disabled;

    /**
     * @param onBargeIn fired on a confirmed run of speech; returns whether it actually
     *                  interrupted anything, which is what decides if the detector disarms
     * @param gate optional STT gate fed the same window scores; null to stream all audio
     * @param amd  optional answering-machine detector fed the same scores; null to skip
     *             detection (§8.6)
     */
    public VadStream(SileroVad model, VadProperties props, String channelId, BooleanSupplier onBargeIn,
                     SpeechGate gate, AnsweringMachineDetector amd) {
        this.model = model;
        this.channelId = channelId;
        this.threshold = props.threshold();
        this.onBargeIn = onBargeIn;
        this.gate = gate;
        this.amd = amd;
        this.window = new float[props.windowSamples()];
        this.state = model.newState();
        int frameMs = Math.max(1, props.windowSamples() * 1000 / props.sampleRate());
        this.minSpeechWindows = Math.max(1, props.minSpeechMs() / frameMs);
        this.silenceResetWindows = Math.max(1, props.silenceResetMs() / frameMs);
    }

    @Override
    public void onAudio(short[] pcm, int length) {
        if (disabled) {
            return;
        }
        for (int i = 0; i < length; i++) {
            window[filled++] = pcm[i] / 32768f;
            if (filled == window.length) {
                filled = 0;
                process();
            }
        }
    }

    private void process() {
        float prob = model.run(window, state);
        if (prob < 0f) {
            // Inference error — stop scoring this stream (barge-in off, call continues).
            disabled = true;
            if (gate != null) {
                // Without scores the gate cannot tell speech from silence, and a stuck
                // gate would cost transcripts. Cost saving yields to recognition.
                gate.bypass();
            }
            log.warn("[{}] VAD disabled after inference error", channelId);
            return;
        }
        boolean speech = prob >= threshold;
        if (gate != null) {
            gate.onVadWindow(speech, window.length);
        }
        if (amd != null && !amd.isFinished()) {
            amd.onVadWindow(speech, window.length);
        }
        if (speech) {
            speechWindows++;
            silenceWindows = 0;
            if (armed && speechWindows >= minSpeechWindows) {
                boolean interrupted;
                try {
                    interrupted = onBargeIn.getAsBoolean();
                } catch (Exception e) {
                    log.warn("[{}] barge-in handler failed: {}", channelId, e.getMessage());
                    interrupted = true; // do not retry against a handler that throws
                }
                if (interrupted) {
                    armed = false;
                    log.info("[{}] barge-in detected (speech {} windows)", channelId, speechWindows);
                } else {
                    // Nothing was interrupted — the bot was not speaking. Start the run
                    // over so the same continuing utterance can try again in another
                    // minSpeechMs, rather than spending the call's one arming on it.
                    speechWindows = 0;
                }
            }
        } else {
            silenceWindows++;
            speechWindows = 0;
            if (!armed && silenceWindows >= silenceResetWindows) {
                armed = true; // ready to detect the next interruption
            }
        }
    }
}
