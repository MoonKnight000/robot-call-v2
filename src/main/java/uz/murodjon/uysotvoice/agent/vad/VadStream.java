package uz.murodjon.uysotvoice.agent.vad;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import uz.murodjon.uysotvoice.agent.audio.AudioListener;

/**
 * Per-call barge-in detector (PROJECT.md §7.2). Buffers a call's decoded 8 kHz PCM
 * into fixed windows, scores each with {@link SileroVad}, and fires the barge-in
 * callback once continuous speech exceeds {@code minSpeechMs} — so short "aha"/"ha"
 * back-channels are ignored. Re-arms after a silence gap for the next utterance.
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
    private final Runnable onBargeIn;

    private final float[] window;
    private final float[][][] state;
    private int filled;
    private int speechWindows;
    private int silenceWindows;
    private boolean armed = true;
    private boolean disabled;

    public VadStream(SileroVad model, VadProperties props, String channelId, Runnable onBargeIn) {
        this.model = model;
        this.channelId = channelId;
        this.threshold = props.threshold();
        this.onBargeIn = onBargeIn;
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
            log.warn("[{}] VAD disabled after inference error", channelId);
            return;
        }
        if (prob >= threshold) {
            speechWindows++;
            silenceWindows = 0;
            if (armed && speechWindows >= minSpeechWindows) {
                armed = false;
                log.info("[{}] barge-in detected (speech {} windows)", channelId, speechWindows);
                try {
                    onBargeIn.run();
                } catch (Exception e) {
                    log.warn("[{}] barge-in handler failed: {}", channelId, e.getMessage());
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
