package uz.murodjon.robotcallv2.agent.stt;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.List;

/**
 * Speech-to-text settings. Bound from {@code voice-agent.stt.*} (PROJECT.md §12).
 *
 * @param enabled         when false, no STT session is opened (e.g. no credentials)
 * @param provider        active provider id: {@code google}, {@code yandex}, {@code aisha}, or {@code deepgram}
 * @param defaultLanguage BCP-47 language used until per-call language selection exists
 * @param detectLanguages every language a caller may answer in, whichever language the
 *                        call was dialled in.
 * @param vadGating       withhold non-speech audio from the provider's (per-second
 *                        billed) stream
 * @param endpointing     decide end-of-utterance here instead of at the provider
 * @param google          Google-specific settings
 * @param yandex          Yandex-specific settings
 * @param aisha           Aisha-specific settings
 * @param deepgram        Deepgram-specific settings
 */
@ConfigurationProperties(prefix = "voice-agent.stt")
public record SttProperties(
        boolean enabled,
        String provider,
        String defaultLanguage,
        List<String> detectLanguages,
        VadGatingProperties vadGating,
        EndpointingProperties endpointing,
        GoogleSttProperties google,
        YandexSttProperties yandex,
        AishaSttProperties aisha,
        DeepgramSttProperties deepgram
) {
}
