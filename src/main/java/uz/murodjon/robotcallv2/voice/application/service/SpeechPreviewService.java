package uz.murodjon.robotcallv2.voice.application.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import uz.murodjon.robotcallv2.agent.audio.AudioTranscoder;
import uz.murodjon.robotcallv2.agent.rtp.WavHeader;
import uz.murodjon.robotcallv2.agent.stt.SttProperties;
import uz.murodjon.robotcallv2.agent.stt.SttProvider;
import uz.murodjon.robotcallv2.agent.stt.SttProviderSelector;
import uz.murodjon.robotcallv2.agent.stt.SttSession;
import uz.murodjon.robotcallv2.agent.stt.TranscriptListener;
import uz.murodjon.robotcallv2.agent.tts.SpeechTextNormalizer;
import uz.murodjon.robotcallv2.agent.tts.TtsProvider;
import uz.murodjon.robotcallv2.agent.tts.TtsProviderSelector;
import uz.murodjon.robotcallv2.company.application.service.CurrentCompany;
import uz.murodjon.robotcallv2.engine.application.service.EngineConfigService;
import uz.murodjon.robotcallv2.shared.exception.ConflictException;
import uz.murodjon.robotcallv2.shared.exception.ErrorCode;
import uz.murodjon.robotcallv2.shared.exception.ExternalServiceException;
import uz.murodjon.robotcallv2.shared.exception.NotFoundException;
import uz.murodjon.robotcallv2.shared.exception.ValidationException;
import uz.murodjon.robotcallv2.voice.application.dto.SttPreviewResponse;
import uz.murodjon.robotcallv2.voice.application.port.input.SpeechPreviewUseCase;
import uz.murodjon.robotcallv2.voice.application.port.input.VoiceSettingsUseCase;
import uz.murodjon.robotcallv2.voice.application.port.output.TtsVoiceRepository;
import uz.murodjon.robotcallv2.voice.domain.entity.EffectiveVoiceSettings;
import uz.murodjon.robotcallv2.voice.domain.entity.TtsVoice;

import java.io.IOException;
import java.util.Arrays;
import java.util.List;

/**
 * Speech vendor try-outs for the settings screen.
 *
 * <p>The voice preview talks to the voice's own {@link TtsProvider} directly rather than
 * through {@code TtsRouter}: the router's job on a live call is to keep talking no matter
 * what — falling back to another vendor's default voice when the chosen one fails — and a
 * preview that quietly played a different voice would tell the operator the opposite of
 * the truth. Here a failing provider is reported as the 502 it is.
 *
 * <p>The STT preview takes whatever the browser recorded ({@link AudioTranscoder}) and
 * feeds it to the recognizer at the recording's own pace, the way a call does, because
 * that is the only rate every streaming endpoint is known to accept. A 30-second file
 * therefore takes about 30 seconds — hence the length cap.
 */
@Service
public class SpeechPreviewService implements SpeechPreviewUseCase {

    private static final Logger log = LoggerFactory.getLogger(SpeechPreviewService.class);

    private static final int TELEPHONE_RATE = 8000;
    private static final int MAX_AUDIO_SECONDS = 30;
    private static final int FRAME_MS = 20;
    /** How long after the audio ends a final may still arrive. */
    private static final long FINAL_WAIT_MS = 4000;
    /** Once a final has arrived, how long a quiet recognizer is given before it is taken as done. */
    private static final long QUIET_MS = 1200;

    private final TtsVoiceRepository ttsVoiceRepository;
    private final TtsProviderSelector ttsProviderSelector;
    private final VoiceSettingsUseCase voiceSettingsUseCase;
    private final SttProviderSelector sttProviderSelector;
    private final SttProperties sttProperties;
    private final AudioTranscoder audioTranscoder;
    private final EngineConfigService engineConfigService;
    private final CurrentCompany currentCompany;

    public SpeechPreviewService(TtsVoiceRepository ttsVoiceRepository, TtsProviderSelector ttsProviderSelector,
                                VoiceSettingsUseCase voiceSettingsUseCase, SttProviderSelector sttProviderSelector,
                                SttProperties sttProperties, AudioTranscoder audioTranscoder,
                                EngineConfigService engineConfigService, CurrentCompany currentCompany) {
        this.ttsVoiceRepository = ttsVoiceRepository;
        this.ttsProviderSelector = ttsProviderSelector;
        this.voiceSettingsUseCase = voiceSettingsUseCase;
        this.sttProviderSelector = sttProviderSelector;
        this.sttProperties = sttProperties;
        this.audioTranscoder = audioTranscoder;
        this.engineConfigService = engineConfigService;
        this.currentCompany = currentCompany;
    }

    @Override
    public byte[] previewVoice(String voiceId, String text) {
        TtsVoice voice = ttsVoiceRepository.find(voiceId);
        if (voice == null) {
            throw new NotFoundException(ErrorCode.TTS_VOICE_NOT_FOUND, voiceId);
        }
        TtsProvider provider = ttsProviderSelector.tryFind(voice.provider());
        if (provider == null) {
            // A realtime engine's voice, or a TTS vendor whose credentials are not configured.
            throw new ConflictException(ErrorCode.TTS_VOICE_PROVIDER_UNAVAILABLE, voiceId, voice.provider());
        }
        EffectiveVoiceSettings style = voiceSettingsUseCase.effective(currentCompany.id()).withRole(voice.role());
        String speech = SpeechTextNormalizer.normalize(text, voice.language());
        short[] pcm = provider.synthesize(speech, voice.language(), voice.name(), style);
        return toWav(pcm);
    }

    @Override
    public SttPreviewResponse previewStt(MultipartFile file, String providerName, String language) {
        if (file == null || file.isEmpty()) {
            throw new ValidationException(ErrorCode.AUDIO_UPLOAD_FILE_MISSING);
        }
        byte[] upload;
        try {
            upload = file.getBytes();
        } catch (IOException e) {
            throw new ValidationException(ErrorCode.AUDIO_UPLOAD_INVALID, e.getMessage());
        }
        SttProvider provider = resolveSttProvider(providerName);
        short[] samples = audioTranscoder.decode(upload, provider.sampleRate());
        long audioMs = samples.length * 1000L / provider.sampleRate();
        if (audioMs > MAX_AUDIO_SECONDS * 1000L) {
            throw new ValidationException(ErrorCode.AUDIO_UPLOAD_TOO_LONG, MAX_AUDIO_SECONDS);
        }
        String lang = (language == null || language.isBlank()) ? sttProperties.defaultLanguage() : language;

        TranscriptCollector collector = new TranscriptCollector();
        List<String> alternatives = sttProperties.detectLanguages() == null ? List.of() : sttProperties.detectLanguages();
        SttSession session = provider.startStream(lang, alternatives, collector, false);
        try {
            stream(session, samples, provider.sampleRate());
            session.endUtterance();
            collector.awaitQuiet();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new ExternalServiceException(ErrorCode.STT_PREVIEW_INTERRUPTED, provider.name());
        } finally {
            closeQuietly(session);
        }
        return new SttPreviewResponse(provider.name(), lang, collector.transcript(), audioMs);
    }

    private SttProvider resolveSttProvider(String providerName) {
        boolean chosenExplicitly = providerName != null && !providerName.isBlank();
        if (chosenExplicitly && !sttProviderSelector.exists(providerName)) {
            throw new ValidationException(ErrorCode.ENGINE_STT_PROVIDER_UNKNOWN, providerName,
                    sttProviderSelector.names());
        }
        String chosen = chosenExplicitly
                ? providerName
                : engineConfigService.findEffectiveByCompanyId(currentCompany.id()).sttProvider();
        SttProvider provider = sttProviderSelector.findForCall(chosen);
        if (provider == null) {
            throw new ConflictException(ErrorCode.STT_PROVIDER_UNAVAILABLE);
        }
        return provider;
    }

    /** 20 ms frames at the audio's own pace, as {@code SttStreamBridge} sends them from a call. */
    private static void stream(SttSession session, short[] samples, int sampleRate) throws InterruptedException {
        int frame = sampleRate * FRAME_MS / 1000;
        byte[] buffer = new byte[frame * 2];
        for (int i = 0; i < samples.length; i += frame) {
            int len = Math.min(frame, samples.length - i);
            for (int j = 0; j < len; j++) {
                buffer[j * 2] = (byte) (samples[i + j] & 0xFF);
                buffer[j * 2 + 1] = (byte) ((samples[i + j] >> 8) & 0xFF);
            }
            session.sendAudio(len == frame ? buffer : Arrays.copyOf(buffer, len * 2));
            Thread.sleep(FRAME_MS);
        }
    }

    private static void closeQuietly(SttSession session) {
        try {
            session.close();
        } catch (IOException e) {
            log.debug("STT preview session close failed: {}", e.getMessage());
        }
    }

    private static byte[] toWav(short[] pcm) {
        byte[] header = WavHeader.bytes(TELEPHONE_RATE, 1, pcm.length * 2);
        byte[] wav = new byte[header.length + pcm.length * 2];
        System.arraycopy(header, 0, wav, 0, header.length);
        for (int i = 0; i < pcm.length; i++) {
            wav[header.length + i * 2] = (byte) (pcm[i] & 0xFF);
            wav[header.length + i * 2 + 1] = (byte) ((pcm[i] >> 8) & 0xFF);
        }
        return wav;
    }

    /** Gathers a stream's finals; falls back to the last interim when no final ever came. */
    private static final class TranscriptCollector implements TranscriptListener {

        private final StringBuilder finals = new StringBuilder();
        private String lastInterim = "";
        private volatile long lastEventAt = System.currentTimeMillis();

        @Override
        public synchronized void onTranscript(String text, boolean isFinal, float confidence) {
            lastEventAt = System.currentTimeMillis();
            if (isFinal) {
                if (!finals.isEmpty()) {
                    finals.append(' ');
                }
                finals.append(text.trim());
            } else {
                lastInterim = text;
            }
        }

        /**
         * Wait for the recognizer to finish: a final followed by {@link #QUIET_MS} of
         * nothing, or {@link #FINAL_WAIT_MS} at most when no final ever arrives.
         */
        void awaitQuiet() throws InterruptedException {
            long deadline = System.currentTimeMillis() + FINAL_WAIT_MS;
            while (System.currentTimeMillis() < deadline) {
                if (hasFinal() && System.currentTimeMillis() - lastEventAt >= QUIET_MS) {
                    return;
                }
                Thread.sleep(100);
            }
        }

        private synchronized boolean hasFinal() {
            return !finals.isEmpty();
        }

        synchronized String transcript() {
            return !finals.isEmpty() ? finals.toString() : lastInterim.trim();
        }
    }
}
