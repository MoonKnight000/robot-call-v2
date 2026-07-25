package uz.murodjon.uysotvoice.agent.stt;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import uz.murodjon.uysotvoice.agent.audio.AudioListener;
import uz.murodjon.uysotvoice.agent.audio.Resampler;

import java.io.Closeable;

/**
 * Feeds a call's decoded 8 kHz PCM into a streaming STT session, upsampling to
 * 16 kHz only when the provider is configured for it, and forwards transcripts to
 * the supplied {@link TranscriptListener} (Stage 5; wired to the dialog engine in
 * Stage 7). DB persistence of transcripts waits for the call-record lifecycle
 * (Stage 9).
 */
public class SttStreamBridge implements AudioListener, Closeable {

    private static final Logger log = LoggerFactory.getLogger(SttStreamBridge.class);

    private final String channelId;
    private final int targetSampleRate;
    private final SttSession session;

    public SttStreamBridge(SttProvider provider, int targetSampleRate, String channelId,
                           String language, TranscriptListener listener) {
        this.channelId = channelId;
        this.targetSampleRate = targetSampleRate;
        this.session = provider.startStream(language, listener);
    }

    @Override
    public void onAudio(short[] pcm, int length) {
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
        } catch (Exception e) {
            log.warn("STT send failed [{}]: {}", channelId, e.getMessage());
        }
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
