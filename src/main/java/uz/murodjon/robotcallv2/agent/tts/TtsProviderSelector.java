package uz.murodjon.robotcallv2.agent.tts;

import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Picks the {@link TtsProvider} one utterance is spoken by (PROJECT.md §2.5). Every
 * implemented provider is registered here; which one a call uses comes from its
 * company's {@code engine_config} via {@link TtsRouter}, and {@code
 * voice-agent.tts.provider} is the default for a company that has not chosen — a
 * misconfigured default fails the app at startup here rather than on the first
 * synthesized line.
 */
@Component
public class TtsProviderSelector {

    private static final Logger log = LoggerFactory.getLogger(TtsProviderSelector.class);

    private final Map<String, TtsProvider> byName = new LinkedHashMap<>();
    private final TtsProvider defaultProvider;

    public TtsProviderSelector(List<TtsProvider> providers, TtsProperties props) {
        for (TtsProvider provider : providers) {
            byName.put(provider.name().toLowerCase(), provider);
        }
        this.defaultProvider = choose(providers, props.provider());
    }

    private static TtsProvider choose(List<TtsProvider> providers, String configured) {
        if (providers == null || providers.isEmpty()) {
            return null;
        }
        if (configured != null && !configured.isBlank()) {
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
        return providerName != null && byName.containsKey(providerName.toLowerCase());
    }

    /**
     * The provider named {@code providerName}, or {@code null} if this build has none by
     * that name — for a caller that already has a fallback of its own ({@link TtsRouter}
     * routing a chosen voice to its own provider) and does not want {@link #findForCall}'s
     * warning-and-default behaviour.
     */
    public TtsProvider tryFind(String providerName) {
        return providerName == null ? null : byName.get(providerName.toLowerCase());
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
        TtsProvider provider = byName.get(providerName.toLowerCase());
        if (provider == null) {
            log.warn("TTS provider '{}' is not implemented in this build — using {}",
                    providerName, defaultProvider != null ? defaultProvider.name() : "none");
            return defaultProvider;
        }
        return provider;
    }
}
