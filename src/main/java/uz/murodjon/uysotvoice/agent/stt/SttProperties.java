package uz.murodjon.uysotvoice.agent.stt;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Speech-to-text settings. Bound from {@code voice-agent.stt.*} (PROJECT.md §12).
 *
 * @param enabled         when false, no STT session is opened (e.g. no credentials)
 * @param provider        active provider id: {@code google}, {@code yandex} or {@code aisha}
 * @param defaultLanguage BCP-47 language used until per-call language selection exists
 * @param vadGating       withhold non-speech audio from the provider's (per-second
 *                        billed) stream
 * @param endpointing     decide end-of-utterance here instead of at the provider, so a
 *                        one-word answer is not made to wait like a long one
 * @param google          Google-specific settings
 * @param yandex          Yandex-specific settings
 * @param aisha           Aisha-specific settings
 */
@ConfigurationProperties(prefix = "voice-agent.stt")
public record SttProperties(
        boolean enabled,
        String provider,
        String defaultLanguage,
        VadGatingProperties vadGating,
        EndpointingProperties endpointing,
        GoogleSttProperties google,
        YandexSttProperties yandex,
        AishaSttProperties aisha
) {
}
