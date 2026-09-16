package uz.murodjon.robotcallv2.agent.flow;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import uz.murodjon.robotcallv2.agent.audio.Resampler;
import uz.murodjon.robotcallv2.agent.rtp.RtpEndpoint;
import uz.murodjon.robotcallv2.agent.rtp.WavAudio;
import uz.murodjon.robotcallv2.agent.rtp.WavReader;
import uz.murodjon.robotcallv2.agent.tts.TtsProperties;
import uz.murodjon.robotcallv2.agent.tts.TtsRouter;
import uz.murodjon.robotcallv2.storage.application.dto.DownloadableFile;
import uz.murodjon.robotcallv2.storage.application.port.input.FileStorageUseCase;

import java.io.InputStream;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Plays what the PBX side of a call says: TTS prompts, uploaded recordings, the beep.
 * Everything goes out through the call's own RTP leg, like the bot's voice — Asterisk has
 * no Uzbek sound files to play, and a synthesized prompt is cached by {@link TtsRouter}
 * after its first use, so a menu costs one synthesis per wording, not per call.
 */
@Component
public class FlowPrompter {

    private static final Logger log = LoggerFactory.getLogger(FlowPrompter.class);
    private static final int SAMPLE_RATE = 8000;
    private static final int RECORDING_CACHE_SIZE = 32;
    /** Beyond the audio's own length, how long playback may lag before it is given up on. */
    private static final long PLAYBACK_SLACK_MS = 1500;
    private static final long POLL_MS = 50;

    private final TtsRouter ttsRouter;
    private final TtsProperties ttsProperties;
    private final FileStorageUseCase fileStorageUseCase;
    /** Keyed by company and file, so a cache hit never skips the tenant check a miss makes. */
    private final Map<String, short[]> recordings = Collections.synchronizedMap(
            new LinkedHashMap<>(16, 0.75f, true) {
                @Override
                protected boolean removeEldestEntry(Map.Entry<String, short[]> eldest) {
                    return size() > RECORDING_CACHE_SIZE;
                }
            });
    private volatile short[] beep;

    public FlowPrompter(TtsRouter ttsRouter, TtsProperties ttsProperties, FileStorageUseCase fileStorageUseCase) {
        this.ttsRouter = ttsRouter;
        this.ttsProperties = ttsProperties;
        this.fileStorageUseCase = fileStorageUseCase;
    }

    /** {@code text} as 8 kHz PCM in {@code language}'s default voice; empty when it cannot be synthesized. */
    public short[] speech(String text, String language) {
        if (text == null || text.isBlank() || !ttsProperties.enabled()) {
            return new short[0];
        }
        String lang = language != null && !language.isBlank() ? language : ttsProperties.defaultLanguage();
        try {
            return ttsRouter.synthesize(text, lang, null);
        } catch (Exception e) {
            log.warn("Prompt could not be synthesized in {}: {}", lang, e.getMessage());
            return new short[0];
        }
    }

    /** An uploaded WAV (greeting, menu, hold music) as 8 kHz PCM; empty when it cannot be read. */
    public short[] recording(long companyId, long fileId) {
        String key = companyId + ":" + fileId;
        short[] cached = recordings.get(key);
        if (cached != null) {
            return cached;
        }
        try {
            DownloadableFile file = fileStorageUseCase.download(companyId, fileId);
            byte[] bytes;
            try (InputStream in = file.content()) {
                bytes = in.readAllBytes();
            }
            WavAudio audio = WavReader.read(bytes);
            short[] pcm = toTelephoneRate(audio.samples(), audio.sampleRate());
            recordings.put(key, pcm);
            return pcm;
        } catch (Exception e) {
            log.warn("Recording {} of company {} could not be played: {}", fileId, companyId, e.getMessage());
            return new short[0];
        }
    }

    public boolean say(FlowCall call, String text, boolean interruptible) {
        return play(call, speech(text, call.language()), interruptible);
    }

    /**
     * Plays {@code pcm} and waits until it has been heard, the caller has hung up, or — when
     * {@code interruptible} — a key was pressed; the key stays queued for whoever reads the
     * menu next. True when a key cut the prompt short.
     */
    public boolean play(FlowCall call, short[] pcm, boolean interruptible) {
        if (pcm == null || pcm.length == 0 || call.isGone()) {
            return false;
        }
        RtpEndpoint endpoint = call.endpoint();
        if (interruptible && call.digits().hasPending()) {
            return true;
        }
        endpoint.playPcm(pcm);
        long deadline = System.currentTimeMillis() + pcm.length * 1000L / SAMPLE_RATE + PLAYBACK_SLACK_MS;
        while (System.currentTimeMillis() < deadline && !call.isGone()) {
            if (interruptible && call.digits().hasPending()) {
                endpoint.flushPlayback();
                return true;
            }
            if (!endpoint.isPlaying()) {
                return false;
            }
            try {
                Thread.sleep(POLL_MS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return false;
            }
        }
        return false;
    }

    /** Silences whatever the call is hearing from us. */
    public void stop(FlowCall call) {
        call.endpoint().flushPlayback();
    }

    /** The voicemail beep: 1 kHz for 400 ms, faded in and out so it does not click. */
    public short[] beep() {
        short[] cached = beep;
        if (cached == null) {
            int length = SAMPLE_RATE * 400 / 1000;
            int fade = SAMPLE_RATE / 100;
            cached = new short[length];
            for (int i = 0; i < length; i++) {
                double envelope = Math.min(1.0, Math.min(i, length - 1 - i) / (double) fade);
                cached[i] = (short) (6000 * envelope * Math.sin(2 * Math.PI * 1000 * i / SAMPLE_RATE));
            }
            beep = cached;
        }
        return cached;
    }

    static short[] toTelephoneRate(short[] samples, int sampleRate) {
        if (sampleRate == SAMPLE_RATE || samples.length == 0) {
            return samples;
        }
        if (sampleRate == 16000) {
            return Resampler.downsample16kTo8k(samples, samples.length);
        }
        if (sampleRate == 24000) {
            return Resampler.downsample24kTo8k(samples, samples.length);
        }
        // Anything else (44.1 kHz music, 22 kHz): linear interpolation is plenty for a phone line.
        int length = (int) ((long) samples.length * SAMPLE_RATE / sampleRate);
        short[] out = new short[length];
        for (int i = 0; i < length; i++) {
            double source = (double) i * sampleRate / SAMPLE_RATE;
            int index = (int) source;
            int next = Math.min(index + 1, samples.length - 1);
            double fraction = source - index;
            out[i] = (short) Math.round(samples[index] * (1 - fraction) + samples[next] * fraction);
        }
        return out;
    }
}
