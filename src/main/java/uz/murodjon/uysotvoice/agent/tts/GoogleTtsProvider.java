package uz.murodjon.uysotvoice.agent.tts;

import com.google.cloud.texttospeech.v1.*;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import uz.murodjon.uysotvoice.agent.rtp.WavReader;

import java.io.IOException;
import java.util.Map;

/**
 * Google Cloud Text-to-Speech provider (PROJECT.md §2.5). Primary voice for
 * {@code uz-UZ}; also usable as an {@code ru-RU} fallback. Uses Application
 * Default Credentials — the same {@code GOOGLE_APPLICATION_CREDENTIALS} as STT.
 * If the client cannot be created the app still starts; TTS is just unavailable.
 *
 * <p>Requests {@code LINEAR16} at 8 kHz, so the returned audio is a WAV container
 * we parse straight into telephone-rate PCM (no resampling on the RTP path).
 * Lower {@code @Order} providers (Yandex) win for the languages they support.
 */
@Component
@Order(20)
@ConditionalOnProperty(prefix = "voice-agent.tts.google", name = "enabled", havingValue = "true", matchIfMissing = true)
public class GoogleTtsProvider implements TtsProvider {

    private static final Logger log = LoggerFactory.getLogger(GoogleTtsProvider.class);

    private final TtsProperties props;
    private volatile TextToSpeechClient client;

    public GoogleTtsProvider(TtsProperties props) {
        this.props = props;
    }

    @PostConstruct
    public void init() {
        try {
            client = TextToSpeechClient.create();
            log.info("Google TTS ready (sampleRate={}, voices={})",
                    props.google().sampleRate(), voices().keySet());
        } catch (Exception e) {
            // Non-fatal: run without Google TTS until credentials are configured.
            log.error("Google TTS unavailable (check GOOGLE_APPLICATION_CREDENTIALS): {}", e.getMessage());
        }
    }

    @Override
    public String name() {
        return "google";
    }

    @Override
    public boolean supports(String language) {
        // §2.5: Google covers Uzbek and (as MVP fallback) Russian.
        return language != null && (language.startsWith("uz") || language.startsWith("ru"));
    }

    @Override
    public short[] synthesize(String text, String language, String requestedVoice) {
        TextToSpeechClient current = client;
        if (current == null) {
            throw new IllegalStateException("Google TTS client is not available");
        }

        SynthesisInput input = SynthesisInput.newBuilder().setText(text).build();

        VoiceSelectionParams.Builder voice = VoiceSelectionParams.newBuilder().setLanguageCode(language);
        // A campaign's chosen voice wins over the per-language default (§2.5).
        String voiceName = (requestedVoice != null && !requestedVoice.isBlank())
                ? requestedVoice
                : voices().get(language);
        if (voiceName != null && !voiceName.isBlank()) {
            voice.setName(voiceName);
        }

        AudioConfig audioConfig = AudioConfig.newBuilder()
                .setAudioEncoding(AudioEncoding.LINEAR16)
                .setSampleRateHertz(props.google().sampleRate())
                .setSpeakingRate(props.google().speakingRate())
                .setPitch(props.google().pitch())
                .build();

        SynthesizeSpeechResponse response = current.synthesizeSpeech(input, voice.build(), audioConfig);
        // LINEAR16 audio comes back wrapped in a WAV container; parse out the PCM.
        try {
            WavReader.WavAudio audio = WavReader.read(response.getAudioContent().toByteArray());
            return audio.samples();
        } catch (IOException e) {
            throw new IllegalStateException("Failed to parse Google TTS audio: " + e.getMessage(), e);
        }
    }

    private Map<String, String> voices() {
        Map<String, String> v = props.google().voices();
        return v != null ? v : Map.of();
    }

    @PreDestroy
    public void shutdown() {
        TextToSpeechClient current = client;
        if (current != null) {
            current.close();
        }
    }
}
