package uz.murodjon.robotcallv2.agent.tts;

import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Resolves the {@link TtsProvider} to speak an utterance with. Follows the same pattern
 * as {@link uz.murodjon.robotcallv2.agent.stt.SttProviderSelector} — multi-tenant
 * deployments let each company pick its own provider in {@code engine_config}, and the
 * app-wide {@code voice-agent.tts.provider} is the default when none is picked.
 */
@Component
public class TtsProviderSelector {

    private static final Logger log = LoggerFactory.getLogger(TtsProviderSelector.class);

    private final Map<String, TtsProvider> byName = new LinkedHashMap<>();
    private final TtsProvider defaultProvider;

    public TtsProviderSelector(List<TtsProvider> providers, TtsProperties ttsProperties) {
        for (TtsProvider provider : providers) {
            byName.put(provider.name().toLowerCase(), provider);
        }
        this.defaultProvider = choose(providers, ttsProperties.provider());
    }

    private static TtsProvider choose(List<TtsProvider> providers, String configured) {
        if (providers == null || providers.isEmpty()) {
            return null;
        }
        if (configured != null && !configured.isBlank()) {
            String target = "google".equalsIgnoreCase(configured) ? "gemini" : configured;
            for (TtsProvider provider : providers) {
                if (provider.name().equalsIgnoreCase(target)) {
                    return provider;
                }
            }
            for (TtsProvider provider : providers) {
                if (provider.name().equalsIgnoreCase(configured)) {
                    return provider;
                }
            }
        }
        return providers.getFirst();
    }

    @PostConstruct
    void logSelection() {
        log.info("TTS providers registered: {} (default {})", byName.keySet(),
                defaultProvider != null ? defaultProvider.name() : "none");
    }

    /** Every provider id this build can be set to — what the settings screen offers. */
    public List<String> names() {
        return byName.values().stream().map(TtsProvider::name).toList();
    }

    /** Whether {@code providerName} is implemented in this build — the check behind a {@code PUT}. */
    public boolean exists(String providerName) {
        if (providerName == null) {
            return false;
        }
        String key = providerName.toLowerCase();
        if ("google".equals(key) && byName.containsKey("gemini")) {
            return true;
        }
        return byName.containsKey(key);
    }

    /**
     * The provider named {@code providerName}, or {@code null} if this build has none by
     * that name — for a caller that already has a fallback of its own ({@link TtsRouter}
     * routing a chosen voice to its own provider) and does not want {@link #findForCall}'s
     * warning-and-default behaviour.
     */
    public TtsProvider tryFind(String providerName) {
        if (providerName == null) {
            return null;
        }
        String key = providerName.toLowerCase();
        if ("google".equals(key) && byName.containsKey("gemini")) {
            return byName.get("gemini");
        }
        return byName.get(key);
    }

    /**
     * The provider for one utterance: the company's chosen id, or the configured default
     * when it chose none.
     *
     * <p>An id that no longer resolves falls back to the default rather than failing the
     * turn — the setting was validated when it was saved, so getting here means the
     * provider left the build afterwards, and a live turn is the worst place to discover
     * that.
     */
    public TtsProvider findForCall(String providerName) {
        if (providerName == null || providerName.isBlank()) {
            return defaultProvider;
        }
        String key = providerName.toLowerCase();
        if ("google".equals(key) && byName.containsKey("gemini")) {
            return byName.get("gemini");
        }
        TtsProvider provider = byName.get(key);
        if (provider == null) {
            log.warn("TTS provider '{}' is not implemented in this build — using {}",
                    providerName, defaultProvider != null ? defaultProvider.name() : "none");
            return defaultProvider;
        }
        return provider;
    }

    /**
     * When a provider fails during synthesis, find an alternate provider if available.
     */
    public TtsProvider findFallback(TtsProvider failed) {
        for (TtsProvider provider : byName.values()) {
            if (provider != failed && (failed == null || !provider.name().equalsIgnoreCase(failed.name()))) {
                return provider;
            }
        }
        return null;
    }
}
