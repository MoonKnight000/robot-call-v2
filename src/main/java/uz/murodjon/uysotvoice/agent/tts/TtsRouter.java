package uz.murodjon.uysotvoice.agent.tts;

import io.micrometer.core.instrument.Timer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import uz.murodjon.uysotvoice.agent.metrics.VoiceMetrics;
import uz.murodjon.uysotvoice.voice.dto.EffectiveVoiceSettings;
import uz.murodjon.uysotvoice.voice.dto.TtsVoice;
import uz.murodjon.uysotvoice.voice.service.TtsVoiceService;

import java.util.ArrayList;
import java.util.List;

/**
 * Synthesizes a request with the {@link TtsProvider} that owns the call's voice
 * (PROJECT.md §2.5). A call may carry a voice chosen when its campaign was created —
 * that choice comes from {@link TtsVoiceService} (the {@code tts_voice} table) — and
 * when it does, {@link TtsProviderSelector} resolves it straight to the provider that
 * owns it, regardless of which provider is that company's {@code engine_config}
 * default: a campaign is free to mix voices from every provider this build has
 * credentials for. Only a call with no voice chosen (or one whose provider left the
 * build) falls back to {@link EffectiveVoiceSettings#provider} (the company's
 * {@code engine_config}), then {@code voice-agent.tts.provider} for a company that
 * chose none.
 *
 * <p>Every request goes through {@link SpeechTextNormalizer} — no provider reads Uzbek
 * numerals correctly — and then {@link TtsCache}: providers bill per character, and the
 * lines this agent repeats most are the short fixed ones.
 */
@Component
public class TtsRouter {

    private static final Logger log = LoggerFactory.getLogger(TtsRouter.class);

    private final TtsProviderSelector selector;
    private final TtsProperties props;
    private final VoiceMetrics metrics;
    private final TtsCache cache;
    private final TtsVoiceService catalog;

    public TtsRouter(TtsProviderSelector selector, TtsProperties props, VoiceMetrics metrics,
                     TtsCache cache, TtsVoiceService catalog) {
        this.selector = selector;
        this.props = props;
        this.metrics = metrics;
        this.cache = cache;
        this.catalog = catalog;
        log.info("TTS router: providers {}", selector.names());
    }

    /** Synthesize with the selected provider's default voice — no campaign voice chosen. */
    public short[] synthesize(String text, String language) {
        return synthesize(text, language, null);
    }

    /**
     * Synthesize {@code text} for {@code language}, returning 8 kHz mono PCM.
     *
     * @param voiceId catalog id of the voice the campaign chose ({@code null} = the
     *                default provider's default voice). Routes to the voice's own
     *                provider; an unknown id, a voice whose provider left the build, or a
     *                call in another language falls back to the default provider and its
     *                default voice rather than failing the turn
     */
    public short[] synthesize(String text, String language, String voiceId) {
        return synthesize(text, language, voiceId, EffectiveVoiceSettings.NONE);
    }

    /**
     * As {@link #synthesize(String, String, String)}, additionally applying a company's
     * §11 settings {@code provider}/{@code speed}/{@code pitch} — resolved once per call by
     * {@code DialogEngine.startCall} and passed in from there. {@code provider} is what
     * decides which vendor speaks this call.
     */
    public short[] synthesize(String text, String language, String voiceId, EffectiveVoiceSettings style) {
        Routed r = route(language, voiceId, style);
        String speech = SpeechTextNormalizer.normalize(text, r.lang());
        log.debug("TTS synth: lang={} voice={} -> provider={}", r.lang(), r.voiceName(), r.provider().name());

        short[] hit = cache.get(r.provider().name(), r.lang(), r.voiceName(), speech, r.style());
        if (hit != null) {
            metrics.ttsCacheHit();
            metrics.ttsCharsSaved(speech.length());
            return hit;
        }

        Timer.Sample sample = metrics.startTimer();
        short[] pcm;
        try {
            pcm = r.provider().synthesize(speech, r.lang(), r.voiceName(), r.style());
        } catch (RuntimeException e) {
            metrics.ttsError();
            throw e;
        } finally {
            metrics.stopTtsSynth(sample);
        }
        metrics.ttsCacheMiss();
        metrics.ttsCharsSynthesized(speech.length());
        cache.put(r.provider().name(), r.lang(), r.voiceName(), speech, pcm, r.style());
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
        log.debug("TTS synth (streaming): lang={} voice={} -> provider={}", r.lang(), r.voiceName(), r.provider().name());

        short[] hit = cache.get(r.provider().name(), r.lang(), r.voiceName(), speech, r.style());
        if (hit != null) {
            metrics.ttsCacheHit();
            metrics.ttsCharsSaved(speech.length());
            onChunk.onChunk(hit);
            return;
        }

        List<short[]> chunks = new ArrayList<>();
        Timer.Sample sample = metrics.startTimer();
        try {
            r.provider().synthesizeStreaming(speech, r.lang(), r.voiceName(), r.style(), pcm -> {
                chunks.add(pcm);
                onChunk.onChunk(pcm);
            });
        } catch (RuntimeException e) {
            metrics.ttsError();
            throw e;
        } finally {
            metrics.stopTtsSynth(sample);
        }
        metrics.ttsCacheMiss();
        metrics.ttsCharsSynthesized(speech.length());
        cache.put(r.provider().name(), r.lang(), r.voiceName(), speech, concat(chunks), r.style());
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

        // A voice a campaign picked speaks through its own provider, not the company's
        // engine_config default — a campaign may mix Yandex and Aisha voices across its
        // targets, each spoken by the provider that owns it.
        TtsVoice chosen = resolve(voiceId, lang);
        TtsProvider ownProvider = chosen != null ? selector.tryFind(chosen.provider()) : null;
        if (ownProvider != null) {
            String effectiveRole = (settings.role() != null && !settings.role().isBlank())
                    ? settings.role()
                    : chosen.role();
            // Nigora in Yandex does not support roles
            if ("yandex".equalsIgnoreCase(chosen.provider()) && "nigora".equalsIgnoreCase(chosen.name())) {
                effectiveRole = null;
            }
            return new Routed(ownProvider, lang, chosen.name(), settings.withRole(effectiveRole));
        }
        if (chosen != null) {
            // The voice's own provider left the build since it was chosen; speak the line
            // in the company's default provider rather than failing the turn.
            log.warn("Voice '{}' needs provider {}, which is not enabled in this build — using the default provider",
                    chosen.id(), chosen.provider());
        }
        TtsProvider provider = selector.findForCall(settings.provider());
        return new Routed(provider, lang, null, settings);
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

}
