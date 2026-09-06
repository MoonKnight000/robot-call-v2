package uz.murodjon.robotcallv2.agent.stt;

import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Resolves the {@link SttProvider} to transcribe a call with. Provider choice lives in a
 * company's {@code engine_config}, and {@code voice-agent.stt.provider} is the default
 * for a company that has not chosen — a misconfigured default fails the app at startup
 * here instead of silently leaving speech recognition off until the first call
 * discovers it missing.
 *
 * <p>This used to resolve a single provider for the whole run. It no longer can: which
 * vendor recognizes a company's Uzbek is that company's setting, and two tenants on one
 * instance may well answer differently.
 */
@Component
public class SttProviderSelector {

    private static final Logger log = LoggerFactory.getLogger(SttProviderSelector.class);

    private final Map<String, SttProvider> byName = new LinkedHashMap<>();
    private final SttProvider defaultProvider;

    public SttProviderSelector(List<SttProvider> providers, SttProperties props) {
        for (SttProvider provider : providers) {
            byName.put(provider.name().toLowerCase(), provider);
        }
        this.defaultProvider = choose(providers, props.provider());
    }

    private static SttProvider choose(List<SttProvider> providers, String configured) {
        if (providers == null || providers.isEmpty()) {
            return null;
        }
        if (configured != null && !configured.isBlank()) {
            String target = "google".equalsIgnoreCase(configured) ? "gemini" : configured;
            for (SttProvider provider : providers) {
                if (provider.name().equalsIgnoreCase(target)) {
                    return provider;
                }
            }
            for (SttProvider provider : providers) {
                if (provider.name().equalsIgnoreCase(configured)) {
                    return provider;
                }
            }
        }
        return providers.getFirst();
    }

    @PostConstruct
    void logSelection() {
        log.info("STT providers registered: {} (default {})", byName.keySet(),
                defaultProvider != null ? defaultProvider.name() : "none");
    }

    /** Every provider id this build can be set to — what the settings screen offers. */
    public List<String> names() {
        return byName.values().stream().map(SttProvider::name).toList();
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
     * The provider for one call: the company's chosen id, or the configured default when
     * it chose none.
     *
     * <p>An id that no longer resolves falls back to the default rather than failing the
     * call — the setting was validated when it was saved, so getting here means the
     * provider left the build afterwards, and a live call is the worst place to discover
     * that.
     */
    public SttProvider findForCall(String providerName) {
        if (providerName == null || providerName.isBlank()) {
            return defaultProvider;
        }
        String key = providerName.toLowerCase();
        if ("google".equals(key) && byName.containsKey("gemini")) {
            return byName.get("gemini");
        }
        SttProvider provider = byName.get(key);
        if (provider == null) {
            log.warn("STT provider '{}' is not implemented in this build — using {}",
                    providerName, defaultProvider != null ? defaultProvider.name() : "none");
            return defaultProvider;
        }
        return provider;
    }

    /**
     * Another provider to recognize with when {@code failed} has stopped working during a
     * call, or {@code null} when this build has only the one.
     *
     * <p>The counterpart of {@code TtsProviderSelector#findFallback}, and needed for the
     * same reason with more at stake: synthesis failing costs one sentence, recognition
     * failing costs the rest of the call. Until this existed
     * {@link SttStreamBridge#reopen()} could only reopen the provider that had just
     * failed, so a vendor that was down — or worse, up and silently returning nothing —
     * left the bot listening to a caller it could no longer hear.
     *
     * <p>Any registered provider will do: a provider is only a bean when its API key is
     * configured ({@code @ConditionalOnExpression} on each), so what is here is what this
     * deployment actually paid for. Language is not checked — every provider in this
     * build is configured for the languages the campaigns dial, and a recognizer with a
     * worse accent is still better than a deaf call.
     */
    public SttProvider findFallback(SttProvider failed) {
        for (SttProvider provider : byName.values()) {
            if (failed == null || !provider.name().equalsIgnoreCase(failed.name())) {
                return provider;
            }
        }
        return null;
    }
}
