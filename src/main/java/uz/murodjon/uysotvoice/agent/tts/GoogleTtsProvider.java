package uz.murodjon.uysotvoice.agent.tts;

import com.google.cloud.texttospeech.v1.*;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.stereotype.Component;

import uz.murodjon.uysotvoice.agent.rtp.WavAudio;
import uz.murodjon.uysotvoice.agent.rtp.WavReader;
import uz.murodjon.uysotvoice.shared.exception.ErrorCode;
import uz.murodjon.uysotvoice.shared.exception.ExternalServiceException;
import uz.murodjon.uysotvoice.voice.dto.EffectiveVoiceSettings;

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
 * Registered whenever {@code GOOGLE_APPLICATION_CREDENTIALS} is set — a company picks
 * this provider per-call via {@code engine_config.tts_provider} (§11 settings), it does
 * not have to be the process-wide {@code voice-agent.tts.provider} default.
 */
@Component
@ConditionalOnExpression("!'${GOOGLE_APPLICATION_CREDENTIALS:}'.isBlank()")
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
        return synthesize(text, language, requestedVoice, EffectiveVoiceSettings.NONE);
    }

    @Override
    public short[] synthesize(String text, String language, String requestedVoice, EffectiveVoiceSettings style) {
        TextToSpeechClient current = client;
        if (current == null) {
            throw new ExternalServiceException(ErrorCode.TTS_GOOGLE_CLIENT_UNAVAILABLE, "google-tts");
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

        // A company's §11 settings/voice override wins over the process default.
        double speakingRate = style != null && style.speed() != null ? style.speed() : props.google().speakingRate();
        double pitch = style != null && style.pitch() != null ? style.pitch() : props.google().pitch();
        AudioConfig audioConfig = AudioConfig.newBuilder()
                .setAudioEncoding(AudioEncoding.LINEAR16)
                .setSampleRateHertz(props.google().sampleRate())
                .setSpeakingRate(speakingRate)
                .setPitch(pitch)
                .build();

        SynthesizeSpeechResponse response = current.synthesizeSpeech(input, voice.build(), audioConfig);
        // LINEAR16 audio comes back wrapped in a WAV container; parse out the PCM.
        try {
            WavAudio audio = WavReader.read(response.getAudioContent().toByteArray());
            return audio.samples();
        } catch (IOException e) {
            throw new ExternalServiceException(ErrorCode.TTS_GOOGLE_AUDIO_PARSE_FAILED, "google-tts", e, e.getMessage());
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
