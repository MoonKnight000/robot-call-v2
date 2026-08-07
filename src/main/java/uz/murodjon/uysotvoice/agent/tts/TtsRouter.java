package uz.murodjon.uysotvoice.agent.tts;

import io.micrometer.core.instrument.Timer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import uz.murodjon.uysotvoice.agent.metrics.VoiceMetrics;
import uz.murodjon.uysotvoice.shared.exception.ConflictException;
import uz.murodjon.uysotvoice.shared.exception.ErrorCode;
import uz.murodjon.uysotvoice.voice.dto.EffectiveVoiceSettings;
import uz.murodjon.uysotvoice.voice.dto.TtsVoice;
import uz.murodjon.uysotvoice.voice.service.TtsVoiceService;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Routes a synthesis request to the right {@link TtsProvider} by language
 * (PROJECT.md §2.5). Providers are injected in {@code @Order} priority, so a
 * specialized provider (Yandex for ru-RU) is tried before the general one
 * (Google). If the requested language has no provider, the configured default
 * language is used as a fallback.
 *
 * <p>A call may also carry a voice chosen when its campaign was created. That choice
 * comes from {@link TtsVoiceService} (the {@code tts_voice} table) and pins the
 * provider too, so language routing only decides calls that did not choose one.
 *
 * <p>Every request goes through {@link SpeechTextNormalizer} — no provider reads Uzbek
 * numerals correctly — and then {@link TtsCache}: providers bill per character, and the
 * lines this agent repeats most are the short fixed ones.
 *
 * <p>A provider that fails mid-call does not end the call: the line is handed to another
 * provider that speaks the language, and the failed one is passed over for a cooldown so
 * the rest of the call does not pay its timeout on every turn. The substitute speaks in
 * its own default voice — a different voice is a far smaller problem than silence, which
 * is what a caller otherwise hears until the no-input watchdog gives up.
 */
@Component
public class TtsRouter {

    private static final Logger log = LoggerFactory.getLogger(TtsRouter.class);

    /**
     * How long a provider that just failed is passed over. Long enough that an outage
     * costs one timeout rather than one per turn, short enough that a single blip does
     * not keep a whole campaign in the substitute's voice.
     */
    private static final Duration PROVIDER_COOLDOWN = Duration.ofSeconds(60);

    /** Provider name → when it may be tried again (epoch millis). */
    private final Map<String, Long> unhealthyUntil = new ConcurrentHashMap<>();

    private final List<TtsProvider> providers;
    private final TtsProperties props;
    private final VoiceMetrics metrics;
    private final TtsCache cache;
    private final TtsVoiceService catalog;

    public TtsRouter(List<TtsProvider> providers, TtsProperties props, VoiceMetrics metrics,
                     TtsCache cache, TtsVoiceService catalog) {
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
        return synthesize(text, language, voiceId, EffectiveVoiceSettings.NONE);
    }

    /**
     * As {@link #synthesize(String, String, String)}, additionally applying a company's
     * §11 settings/voice overrides ({@code provider}/{@code speed}/{@code pitch}) —
     * resolved once per call by {@code DialogEngine.startCall} and passed in from there.
     * The overridden provider only wins when a campaign voice was not already chosen
     * (that already pins its own provider) and it can actually serve the language.
     */
    public short[] synthesize(String text, String language, String voiceId, EffectiveVoiceSettings style) {
        Routed r = route(language, voiceId, style);
        String speech = SpeechTextNormalizer.normalize(text, r.lang());
        log.debug("TTS route: lang={} voice={} -> provider={}", r.lang(), r.voiceName(), r.provider().name());

        short[] hit = cache.get(r.provider().name(), r.lang(), r.voiceName(), speech, r.style());
        if (hit != null) {
            metrics.ttsCacheHit();
            metrics.ttsCharsSaved(speech.length());
            return hit;
        }

        Timer.Sample sample = metrics.startTimer();
        Routed used = r;
        short[] pcm;
        try {
            pcm = r.provider().synthesize(speech, r.lang(), r.voiceName(), r.style());
        } catch (RuntimeException e) {
            metrics.ttsError();
            Routed alternative = failoverRoute(r, e);
            if (alternative == null) {
                throw e;
            }
            used = alternative;
            pcm = alternative.provider().synthesize(speech, alternative.lang(), alternative.voiceName(),
                    alternative.style());
        } finally {
            metrics.stopTtsSynth(sample);
        }
        metrics.ttsCacheMiss();
        metrics.ttsCharsSynthesized(speech.length());
        cache.put(used.provider().name(), used.lang(), used.voiceName(), speech, pcm, used.style());
        return pcm;
    }

    /**
     * As {@link #synthesize(String, String, String, EffectiveVoiceSettings)}, but delivers
     * PCM to {@code onChunk} as the provider produces it instead of waiting for the whole
     * sentence. Meant for the one sentence per turn where that head start actually matters
     * (DialogEngine.speakStreaming, §1.3) — most callers want the plain {@code synthesize}.
     *
     * <p>A cache hit goes out as a single chunk (nothing to stream — it's already in
     * memory). A miss is also collected as it streams and cached once complete, so a
     * repeated sentence still gets the cache's fast path next time.
     */
    public void synthesizeStreaming(String text, String language, String voiceId, EffectiveVoiceSettings style,
                                    PcmChunkListener onChunk) {
        Routed r = route(language, voiceId, style);
        String speech = SpeechTextNormalizer.normalize(text, r.lang());
        log.debug("TTS route (streaming): lang={} voice={} -> provider={}", r.lang(), r.voiceName(), r.provider().name());

        short[] hit = cache.get(r.provider().name(), r.lang(), r.voiceName(), speech, r.style());
        if (hit != null) {
            metrics.ttsCacheHit();
            metrics.ttsCharsSaved(speech.length());
            onChunk.onChunk(hit);
            return;
        }

        List<short[]> chunks = new ArrayList<>();
        Timer.Sample sample = metrics.startTimer();
        Routed used = r;
        try {
            r.provider().synthesizeStreaming(speech, r.lang(), r.voiceName(), r.style(), pcm -> {
                chunks.add(pcm);
                onChunk.onChunk(pcm);
            });
        } catch (RuntimeException e) {
            metrics.ttsError();
            // Only worth failing over while nothing has been spoken yet. Once part of the
            // sentence is on the wire, a second provider would restart it from the top in
            // a different voice — worse for the caller than the sentence simply stopping.
            Routed alternative = chunks.isEmpty() ? failoverRoute(r, e) : null;
            if (alternative == null) {
                throw e;
            }
            used = alternative;
            alternative.provider().synthesizeStreaming(speech, alternative.lang(), alternative.voiceName(),
                    alternative.style(), pcm -> {
                        chunks.add(pcm);
                        onChunk.onChunk(pcm);
                    });
        } finally {
            metrics.stopTtsSynth(sample);
        }
        metrics.ttsCacheMiss();
        metrics.ttsCharsSynthesized(speech.length());
        cache.put(used.provider().name(), used.lang(), used.voiceName(), speech, concat(chunks), used.style());
    }

    /**
     * The provider + resolved language/voice a request routes to, shared by both
     * synthesize methods. {@code style} is the caller's settings with the chosen voice's
     * catalog role folded in, so it is the one that must reach the provider and the cache.
     */
    private record Routed(TtsProvider provider, String lang, String voiceName, EffectiveVoiceSettings style) {
    }

    private Routed route(String language, String voiceId, EffectiveVoiceSettings style) {
        String lang = (language == null || language.isBlank()) ? props.defaultLanguage() : language;
        EffectiveVoiceSettings settings = style != null ? style : EffectiveVoiceSettings.NONE;

        TtsVoice chosen = resolve(voiceId, lang);
        TtsProvider provider = null;
        String voiceName = null;
        if (chosen != null) {
            provider = byName(chosen.provider());
            if (provider == null) {
                log.warn("Voice '{}' needs provider {}, which is not enabled — using default routing",
                        chosen.id(), chosen.provider());
            } else if (!healthy(provider)) {
                // Honouring the pinned voice here would hand every turn of every call to
                // the provider that is already known to be down, only to fail over again.
                log.warn("Voice '{}' needs provider {}, which just failed — using default routing until it recovers",
                        chosen.id(), chosen.provider());
                provider = null;
            } else {
                voiceName = chosen.name();
                // Only a catalog voice carries a role, and only its own: default routing
                // speaks role-less, which is the one setting every voice accepts.
                settings = settings.withRole(chosen.role());
            }
        }
        if (provider == null) {
            provider = select(lang, settings.provider());
        }
        if (provider == null) {
            throw new ConflictException(ErrorCode.TTS_PROVIDER_UNAVAILABLE, lang);
        }
        return new Routed(provider, lang, voiceName, settings);
    }

    /**
     * Where to send a line whose provider just failed, or {@code null} when nobody else
     * can speak the language and the caller's turn really is lost.
     *
     * <p>The failed provider is put on cooldown first, so the rest of this call — and
     * every other call in flight — routes past it instead of discovering the same
     * outage one timeout at a time.
     */
    private Routed failoverRoute(Routed failed, RuntimeException cause) {
        markUnhealthy(failed.provider());
        for (TtsProvider provider : providers) {
            if (provider == failed.provider() || !provider.supports(failed.lang()) || !healthy(provider)) {
                continue;
            }
            log.warn("TTS provider {} failed ({}) — this line goes to {} instead",
                    failed.provider().name(), cause.getMessage(), provider.name());
            metrics.ttsFailover();
            // The campaign's chosen voice and its role belong to the provider that just
            // failed; the substitute speaks in its own configured voice for the language.
            return new Routed(provider, failed.lang(), null, failed.style().withRole(null));
        }
        log.error("TTS provider {} failed ({}) and no other provider speaks {} — the line is lost",
                failed.provider().name(), cause.getMessage(), failed.lang());
        return null;
    }

    private void markUnhealthy(TtsProvider provider) {
        unhealthyUntil.put(provider.name(), System.currentTimeMillis() + PROVIDER_COOLDOWN.toMillis());
    }

    /** Whether {@code provider} is past the cooldown of its last failure. */
    private boolean healthy(TtsProvider provider) {
        Long until = unhealthyUntil.get(provider.name());
        return until == null || until <= System.currentTimeMillis();
    }

    private static short[] concat(List<short[]> chunks) {
        int total = 0;
        for (short[] chunk : chunks) {
            total += chunk.length;
        }
        short[] out = new short[total];
        int pos = 0;
        for (short[] chunk : chunks) {
            System.arraycopy(chunk, 0, out, pos, chunk.length);
            pos += chunk.length;
        }
        return out;
    }

    /**
     * The catalog entry for {@code voiceId}, or {@code null} if it should not be used
     * for this call.
     *
     * <p>A voice only applies to the language it speaks: a campaign whose default is
     * Uzbek may still hold a target marked ru-RU, and having the Uzbek voice read
     * Russian text out is worse than the provider's own Russian voice.
     */
    private TtsVoice resolve(String voiceId, String language) {
        if (voiceId == null || voiceId.isBlank()) {
            return null;
        }
        TtsVoice voice = catalog.find(voiceId);
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

    /**
     * @param companyOverride a company's §11 settings/voice preferred provider, checked
     *                        before the process-wide default; {@code null} defers to it
     */
    private TtsProvider select(String language, String companyOverride) {
        // Providers on cooldown are skipped on the first pass, and taken on the second:
        // one that failed a minute ago is still a better bet than not speaking at all.
        TtsProvider provider = firstSupporting(language, companyOverride, true);
        return provider != null ? provider : firstSupporting(language, companyOverride, false);
    }

    /** @param healthyOnly whether providers still inside their failure cooldown are skipped */
    private TtsProvider firstSupporting(String language, String companyOverride, boolean healthyOnly) {
        // Preferred provider first, if it can serve this language: the company's own
        // override (§11 settings) wins over the process-wide default.
        String preferred = (companyOverride != null && !companyOverride.isBlank())
                ? companyOverride : props.provider();
        if (preferred != null && !preferred.isBlank()) {
            for (TtsProvider provider : providers) {
                if (provider.name().equalsIgnoreCase(preferred) && provider.supports(language)
                        && (!healthyOnly || healthy(provider))) {
                    return provider;
                }
            }
        }
        for (TtsProvider provider : providers) {
            if (provider.supports(language) && (!healthyOnly || healthy(provider))) {
                return provider;
            }
        }
        // Fall back to whatever serves the default language.
        String fallback = props.defaultLanguage();
        if (!fallback.equals(language)) {
            for (TtsProvider provider : providers) {
                if (provider.supports(fallback) && (!healthyOnly || healthy(provider))) {
                    log.warn("No TTS provider for {}, falling back to {} ({})", language, fallback, provider.name());
                    return provider;
                }
            }
        }
        return null;
    }
}
