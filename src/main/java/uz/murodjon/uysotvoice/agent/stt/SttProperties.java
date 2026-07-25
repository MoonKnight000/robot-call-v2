package uz.murodjon.uysotvoice.agent.stt;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Speech-to-text settings. Bound from {@code voice-agent.stt.*} (PROJECT.md §12).
 *
 * @param enabled         when false, no STT session is opened (e.g. no credentials)
 * @param provider        active provider id: {@code google} or {@code yandex}
 * @param defaultLanguage BCP-47 language used until per-call language selection exists
 * @param google          Google-specific settings
 * @param yandex          Yandex-specific settings
 */
@ConfigurationProperties(prefix = "voice-agent.stt")
public record SttProperties(
        boolean enabled,
        String provider,
        String defaultLanguage,
        Google google,
        Yandex yandex
) {

    /**
     * @param model             recognition model ({@code phone_call} for telephony)
     * @param sampleRate        audio sample rate sent to Google (8000 = native telephone)
     * @param enablePunctuation enable automatic punctuation
     * @param endpointingSilenceMs silence that marks end of an utterance
     */
    public record Google(
            String model,
            int sampleRate,
            boolean enablePunctuation,
            int endpointingSilenceMs
    ) {
    }

    /**
     * Yandex SpeechKit STT v3 streaming (gRPC, bidirectional). Real-time recognition
     * with server-side endpointing (EOU) — no client-side utterance buffering. v3
     * supports Uzbek ({@code uz-UZ}) as well as {@code ru-RU}.
     *
     * @param apiKey         SpeechKit API key (gRPC metadata {@code authorization: Api-Key ...})
     * @param folderId       Yandex Cloud folder id (sent as {@code x-folder-id}; optional with an API key)
     * @param host           gRPC endpoint host
     * @param port           gRPC endpoint port (443, TLS)
     * @param sampleRate     raw LINEAR16_PCM input rate (8000 telephone)
     * @param model          recognition model ({@code general} by default; blank = server default)
     * @param interimResults deliver partial (interim) hypotheses in addition to finals
     */
    public record Yandex(
            String apiKey,
            String folderId,
            String host,
            int port,
            int sampleRate,
            String model,
            boolean interimResults
    ) {
    }
}
