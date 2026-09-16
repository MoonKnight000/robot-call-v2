package uz.murodjon.robotcallv2.agent.stt;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.List;

/**
 * Speech-to-text settings. Bound from {@code voice-agent.stt.*} (PROJECT.md §12).
 *
 * @param enabled           when false, no STT session is opened (e.g. no credentials)
 * @param provider          active provider id: {@code gemini}, {@code yandex}, {@code aisha}, or {@code deepgram}
 * @param defaultLanguage   BCP-47 language used until per-call language selection exists
 * @param detectLanguages   every language a caller may answer in, whichever language the
 *                          call was dialled in — a SpeechKit whitelist. More than one
 *                          turns on per-sentence detection, and on a uz-UZ + ru-RU stream
 *                          that cost more than it bought: the interims came back decoded
 *                          as Russian and the final was re-decoded as Uzbek ("распутай"
 *                          and "yahshi bo'lsa keyingi oydan to'la boshlayman" were the
 *                          same audio), which is dead weight inside the finalization the
 *                          caller waits through. A single language also gives up answering
 *                          the caller in the other one — {@code ClientInputGate#adaptLanguage}
 *                          needs two candidates — so put ru-RU back when calls really are
 *                          bilingual
 * @param vadGating         withhold non-speech audio from the provider's (per-second
 *                          billed) stream
 * @param endpointing       decide end-of-utterance here instead of at the provider
 * @param responseTimeoutMs how long a stream may take audio without returning a single
 *                          transcript before it is treated as broken and replaced. Has to
 *                          outlast the longest silence a caller can produce, because
 *                          without VAD gating that silence is streamed too; 0 disables it
 * @param stream            how one call's stream protects itself: replay buffer, promoted-final
 *                          window, provider failover ({@link SttStreamProperties})
 * @param gemini            Gemini STT settings (gemini-3.5-transcribe-live)
 * @param google            Google-specific settings (legacy)
 * @param yandex            Yandex-specific settings
 * @param aisha             Aisha-specific settings
 * @param deepgram          Deepgram-specific settings
 */
@ConfigurationProperties(prefix = "voice-agent.stt")
public record SttProperties(
        boolean enabled,
        String provider,
        String defaultLanguage,
        List<String> detectLanguages,
        VadGatingProperties vadGating,
        EndpointingProperties endpointing,
        int responseTimeoutMs,
        SttStreamProperties stream,
        GeminiSttProperties gemini,
        GoogleSttProperties google,
        YandexSttProperties yandex,
        AishaSttProperties aisha,
        DeepgramSttProperties deepgram,
        OpenAiSttProperties openAi
) {

    public SttProperties {
        if (stream == null) {
            stream = SttStreamProperties.DEFAULTS;
        }
    }
}
