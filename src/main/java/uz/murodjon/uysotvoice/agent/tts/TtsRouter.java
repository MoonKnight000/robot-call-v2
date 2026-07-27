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
 *
 * <p>A call may also carry a voice chosen when its campaign was created. That choice
 * comes from {@link TtsVoiceCatalog} and pins the provider too, so language routing
 * only decides calls that did not choose one.
 *
 * <p>Every request goes through {@link TtsCache} first: providers bill per character,
 * and the lines this agent repeats most are the short fixed ones.
 */
@Component
public class TtsRouter {

    private static final Logger log = LoggerFactory.getLogger(TtsRouter.class);

    private final List<TtsProvider> providers;
    private final TtsProperties props;
    private final VoiceMetrics metrics;
    private final TtsCache cache;
    private final TtsVoiceCatalog catalog;

    public TtsRouter(List<TtsProvider> providers, TtsProperties props, VoiceMetrics metrics,
                     TtsCache cache, TtsVoiceCatalog catalog) {
        this.providers = providers;
        this.props = props;
        this.metrics = metrics;
        this.cache = cache;
        this.catalog = catalog;
        log.info("TTS router: {} provider(s) {}",
                providers.size(), providers.stream().map(TtsProvider::name).toList());
    }

    /** Synthesize with the configured routing — no campaign voice chosen. */
    public short[] synthesize(String text, String language) {
        return synthesize(text, language, null);
    }

    /**
     * Synthesize {@code text} for {@code language}, returning 8 kHz mono PCM.
     *
     * @param voiceId catalog id of the voice the campaign chose ({@code null} = the
     *                configured routing). It pins the provider as well as the voice;
     *                an unknown id, a disabled provider, or a call in another language
     *                falls back to normal routing rather than failing the turn
     * @throws IllegalStateException if no provider can serve the language
     */
    public short[] synthesize(String text, String language, String voiceId) {
        String lang = (language == null || language.isBlank()) ? props.defaultLanguage() : language;

        TtsProperties.Voice chosen = resolve(voiceId, lang);
        TtsProvider provider = null;
        String voiceName = null;
        if (chosen != null) {
            provider = byName(chosen.provider());
            if (provider != null) {
                voiceName = chosen.name();
            } else {
                log.warn("Voice '{}' needs provider {}, which is not enabled — using default routing",
                        chosen.id(), chosen.provider());
            }
        }
        if (provider == null) {
            provider = select(lang);
        }
        if (provider == null) {
            throw new IllegalStateException("No TTS provider available for language " + lang);
        }
        log.debug("TTS route: lang={} voice={} -> provider={}", lang, voiceName, provider.name());

        short[] hit = cache.get(provider.name(), lang, voiceName, text);
        if (hit != null) {
            metrics.ttsCacheHit();
            metrics.ttsCharsSaved(text.length());
            return hit;
        }

        Timer.Sample sample = metrics.startTimer();
        short[] pcm;
        try {
            pcm = provider.synthesize(text, lang, voiceName);
        } catch (RuntimeException e) {
            metrics.ttsError();
            throw e;
        } finally {
            metrics.stopTtsSynth(sample);
        }
        metrics.ttsCacheMiss();
        metrics.ttsCharsSynthesized(text.length());
        cache.put(provider.name(), lang, voiceName, text, pcm);
        return pcm;
    }

    /**
     * The catalog entry for {@code voiceId}, or {@code null} if it should not be used
     * for this call.
     *
     * <p>A voice only applies to the language it speaks: a campaign whose default is
     * Uzbek may still hold a target marked ru-RU, and having the Uzbek voice read
     * Russian text out is worse than the provider's own Russian voice.
     */
    private TtsProperties.Voice resolve(String voiceId, String language) {
        if (voiceId == null || voiceId.isBlank()) {
            return null;
        }
        TtsProperties.Voice voice = catalog.find(voiceId);
        if (voice == null) {
            log.warn("Unknown TTS voice '{}' — using default routing", voiceId);
            return null;
        }
        if (!sameLanguage(voice.language(), language)) {
            log.debug("Voice '{}' speaks {}, call is {} — using default routing",
                    voice.id(), voice.language(), language);
            return null;
        }
        return voice;
    }

    /** Same language, ignoring the region: a {@code ru} voice serves {@code ru-RU}. */
    private static boolean sameLanguage(String a, String b) {
        return a != null && b != null && prefix(a).equals(prefix(b));
    }

    private static String prefix(String language) {
        int dash = language.indexOf('-');
        return (dash > 0 ? language.substring(0, dash) : language).toLowerCase();
    }

    private TtsProvider byName(String name) {
        for (TtsProvider provider : providers) {
            if (provider.name().equalsIgnoreCase(name)) {
                return provider;
            }
        }
        return null;
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
