package uz.murodjon.robotcallv2.agent.vad;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import uz.murodjon.robotcallv2.agent.audio.AnsweringMachineDetector;
import uz.murodjon.robotcallv2.agent.audio.AudioListener;
import uz.murodjon.robotcallv2.agent.audio.SpeechGate;

import java.util.function.BooleanSupplier;
import java.util.function.IntConsumer;

/**
 * Scores incoming audio with {@link SileroVad} in fixed-size windows and fires barge-in
 * when enough consecutive windows contain speech (PROJECT.md §7.2, §12).
 *
 * <p>When a {@link SpeechGate} is supplied, each window's speech/silence decision is
 * also forwarded to it so the gate can open and close the STT stream. The two use the
 * same scores but different windows: barge-in requires {@code minSpeechMs} of speech
 * before it will cut off the bot — otherwise every cough, breath, or short "aha"
 * would silence the agent. Meanwhile, quiet speakers during listening phases are
 * detected via an adaptive {@code listeningThreshold}.
 *
 * <p>Runs on the RTP consumer thread; Silero inference on a 32ms window is cheap.
 */
public class VadStream implements AudioListener {

    private static final Logger log = LoggerFactory.getLogger(VadStream.class);

    private final SileroVad model;
    private final String channelId;
    private final float threshold;
    private final float listeningThreshold;
    private final double minEnergyRms;
    private final int minSpeechWindows;
    private final int silenceResetWindows;
    private final BooleanSupplier onBargeIn;
    /** Fed every scored window; null when STT gating is off. */
    private final SpeechGate gate;
    /** Fed every scored window; null when answering-machine detection is off. */
    private final AnsweringMachineDetector amd;
    /** Told, with the silence that confirmed it, that a run of speech has ended. */
    private final IntConsumer onSpeechEnd;
    /** The silence this stream waits out before calling an utterance over, in ms. */
    private final int speechEndWaitMs;

    private final float[] window;
    private final float[][][] state;
    /** The tail of the window before this one — see {@link SileroVad#run}. */
    private final float[] context;
    private int filled;
    private int speechWindows;
    private int silenceWindows;
    private boolean armed = true;
    private boolean disabled;
    /** Whether a confirmed run of speech is still waiting for its silence. */
    private boolean utteranceOpen;

    /**
     * @param onBargeIn fired on a confirmed run of speech; returns whether it actually
     *                  interrupted anything, which is what decides if the detector disarms
     * @param gate optional STT gate fed the same window scores; null to stream all audio
     * @param amd  optional answering-machine detector fed the same scores; null to skip
     *             detection (§8.6)
     * @param onSpeechEnd told when a confirmed run of speech has been followed by
     *                    {@code silenceResetMs} of silence, with that silence in ms —
     *                    the only signal that the caller stopped talking when the
     *                    recognizer, not a {@link SpeechGate}, decides where the
     *                    utterance ends; null to report nothing
     */
    public VadStream(SileroVad model, VadProperties props, String channelId, BooleanSupplier onBargeIn,
                     SpeechGate gate, AnsweringMachineDetector amd, IntConsumer onSpeechEnd) {
        this.model = model;
        this.channelId = channelId;
        this.threshold = props.threshold();
        this.listeningThreshold = props.listeningThreshold() > 0f ? props.listeningThreshold() : 0.35f;
        this.minEnergyRms = props.minEnergyRms() > 0 ? props.minEnergyRms() : 150.0;
        this.onBargeIn = onBargeIn;
        this.gate = gate;
        this.amd = amd;
        this.onSpeechEnd = onSpeechEnd != null ? onSpeechEnd : ms -> { };
        this.window = new float[props.windowSamples()];
        this.state = model.newState();
        this.context = model.newContext();
        int frameMs = Math.max(1, props.windowSamples() * 1000 / props.sampleRate());
        this.minSpeechWindows = Math.max(1, props.minSpeechMs() / frameMs);
        this.silenceResetWindows = Math.max(1, props.silenceResetMs() / frameMs);
        this.speechEndWaitMs = silenceResetWindows * frameMs;
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
        float prob = model.run(window, state, context);
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

        // Calculate RMS audio energy to protect against low-energy microphone hiss, breath, and road noise
        double sumSq = 0.0;
        for (float sample : window) {
            double sampleShort = sample * 32768.0;
            sumSq += sampleShort * sampleShort;
        }
        double rms = Math.sqrt(sumSq / window.length);

        // For barge-in: strict probability and energy check to avoid false barge-ins from short coughs/noise
        boolean bargeInSpeech = prob >= threshold && rms >= minEnergyRms;

        // For gating / listening: sensitive threshold so quiet or slow speakers are not missed when bot is listening
        boolean gateSpeech = bargeInSpeech || (prob >= listeningThreshold && rms >= (minEnergyRms * 0.4));

        if (gate != null) {
            gate.onVadWindow(gateSpeech, window.length);
        }
        if (amd != null && !amd.isFinished()) {
            amd.onVadWindow(bargeInSpeech, window.length);
        }

        if (bargeInSpeech) {
            speechWindows++;
            silenceWindows = 0;
            if (speechWindows >= minSpeechWindows) {
                utteranceOpen = true;
            }
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
                    log.info("[{}] barge-in detected (speech {} windows, rms={})", channelId, speechWindows, (int) rms);
                } else {
                    speechWindows = 0;
                }
            }
        } else {
            silenceWindows++;
            speechWindows = 0;
            if (utteranceOpen && silenceWindows == silenceResetWindows) {
                utteranceOpen = false;
                onSpeechEnd.accept(speechEndWaitMs);
            }
            if (!armed && silenceWindows >= silenceResetWindows) {
                armed = true; // ready to detect the next interruption
            }
        }
    }
}
