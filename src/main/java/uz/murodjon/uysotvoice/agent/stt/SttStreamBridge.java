package uz.murodjon.uysotvoice.agent.stt;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import uz.murodjon.uysotvoice.agent.audio.AudioListener;
import uz.murodjon.uysotvoice.agent.audio.Resampler;
import uz.murodjon.uysotvoice.agent.audio.SpeechGate;
import uz.murodjon.uysotvoice.agent.metrics.VoiceMetrics;

import java.io.Closeable;

/**
 * Feeds a call's decoded 8 kHz PCM into a streaming STT session, upsampling to
 * 16 kHz only when the provider is configured for it, and forwards transcripts to
 * the supplied {@link TranscriptListener} (Stage 5; wired to the dialog engine in
 * Stage 7).
 *
 * <p>When a {@link SpeechGate} is supplied, only audio the VAD considers speech (plus
 * its pre-roll and post-roll margins) reaches the provider — the rest is counted and
 * dropped. Providers bill per streamed second, and most of a call is not speech. The
 * gate is optional and fails open: no VAD, or a VAD error, means every frame is sent
 * exactly as before.
 */
public class SttStreamBridge implements AudioListener, Closeable {

    private static final Logger log = LoggerFactory.getLogger(SttStreamBridge.class);

    private final String channelId;
    private final int targetSampleRate;
    private final int sourceSampleRate;
    private final SttSession session;
    private final SpeechGate gate;
    private final VoiceMetrics metrics;

    /** Whether the previous frame was sent — only used to log gate transitions. */
    private boolean streaming = true;

    public SttStreamBridge(SttProvider provider, int targetSampleRate, int sourceSampleRate, String channelId,
                           String language, TranscriptListener listener, SpeechGate gate, VoiceMetrics metrics) {
        this.channelId = channelId;
        this.targetSampleRate = targetSampleRate;
        this.sourceSampleRate = sourceSampleRate;
        this.gate = gate;
        this.metrics = metrics;
        this.session = provider.startStream(language, listener);
    }

    @Override
    public void onAudio(short[] pcm, int length) {
        // The VAD listener runs before this one on the same thread, so the gate has
        // already scored the frame in hand — an utterance opens the gate on its own
        // first frame rather than the next one.
        if (gate != null && !gate.isOpen()) {
            gate.buffer(pcm, length);
            metrics.sttAudioSkipped(seconds(length));
            if (streaming) {
                streaming = false;
                log.debug("[{}] STT stream gated (silence)", channelId);
            }
            return;
        }
        if (gate != null && !streaming) {
            streaming = true;
            log.debug("[{}] STT stream resumed (speech)", channelId);
            short[] preRoll = gate.drainPreRoll();
            if (preRoll != null) {
                // The run-up to the first detected window: without it the provider
                // hears an utterance that starts mid-word.
                send(preRoll, preRoll.length);
            }
        }
        send(pcm, length);
    }

    private void send(short[] pcm, int length) {
        short[] samples;
        int len;
        if (targetSampleRate == 16000) {
            samples = Resampler.upsample8kTo16k(pcm, length);
            len = samples.length;
        } else {
            samples = pcm;
            len = length;
        }
        try {
            session.sendAudio(toLittleEndian(samples, len));
            metrics.sttAudioSent(seconds(length));
        } catch (Exception e) {
            log.warn("STT send failed [{}]: {}", channelId, e.getMessage());
        }
    }

    /** Duration of a frame at the source rate — what the provider bills, upsampled or not. */
    private double seconds(int samples) {
        return (double) samples / sourceSampleRate;
    }

    @Override
    public void close() {
        try {
            session.close();
        } catch (Exception e) {
            log.warn("STT close failed [{}]: {}", channelId, e.getMessage());
        }
    }

    private static byte[] toLittleEndian(short[] pcm, int len) {
        byte[] out = new byte[len * 2];
        for (int i = 0; i < len; i++) {
            short s = pcm[i];
            out[i * 2] = (byte) (s & 0xFF);
            out[i * 2 + 1] = (byte) ((s >> 8) & 0xFF);
        }
        return out;
    }
}
