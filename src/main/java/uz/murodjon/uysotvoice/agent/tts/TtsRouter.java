package uz.murodjon.uysotvoice.agent.tts;

import io.micrometer.core.instrument.Timer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import uz.murodjon.uysotvoice.agent.metrics.VoiceMetrics;

import java.util.List;

/**
 * Routes a synthesis request to the right {@link TtsProvider} by language
 * (PROJECT.md §2.5). Providers are injected in {@code @Order} priority, so a
 * specialized provider (Yandex for ru-RU) is tried before the general one
 * (Google). If the requested language has no provider, the configured default
 * language is used as a fallback.
 */
@Component
public class TtsRouter {

    private static final Logger log = LoggerFactory.getLogger(TtsRouter.class);

    private final List<TtsProvider> providers;
    private final TtsProperties props;
    private final VoiceMetrics metrics;

    public TtsRouter(List<TtsProvider> providers, TtsProperties props, VoiceMetrics metrics) {
        this.providers = providers;
        this.props = props;
        this.metrics = metrics;
        log.info("TTS router: {} provider(s) {}", providers.size(), providers.stream().map(TtsProvider::name).toList());
    }

    /**
     * Synthesize {@code text} for {@code language}, returning 8 kHz mono PCM.
     *
     * @throws IllegalStateException if no provider can serve the language
     */
    public short[] synthesize(String text, String language) {
        String lang = (language == null || language.isBlank()) ? props.defaultLanguage() : language;
        TtsProvider provider = select(lang);
        if (provider == null) {
            throw new IllegalStateException("No TTS provider available for language " + lang);
        }
        log.debug("TTS route: lang={} -> provider={}", lang, provider.name());
        Timer.Sample sample = metrics.startTimer();
        try {
            return provider.synthesize(text, lang);
        } catch (RuntimeException e) {
            metrics.ttsError();
            throw e;
        } finally {
            metrics.stopTtsSynth(sample);
        }
    }

    private TtsProvider select(String language) {
        // Preferred provider first, if it can serve this language.
        String preferred = props.provider();
        if (preferred != null && !preferred.isBlank()) {
            for (TtsProvider provider : providers) {
                if (provider.name().equalsIgnoreCase(preferred) && provider.supports(language)) {
                    return provider;
                }
            }
        }
        for (TtsProvider provider : providers) {
            if (provider.supports(language)) {
                return provider;
            }
        }
        // Fall back to whatever serves the default language.
        String fallback = props.defaultLanguage();
        if (!fallback.equals(language)) {
            for (TtsProvider provider : providers) {
                if (provider.supports(fallback)) {
                    log.warn("No TTS provider for {}, falling back to {} ({})", language, fallback, provider.name());
                    return provider;
                }
            }
        }
        return null;
    }
}
