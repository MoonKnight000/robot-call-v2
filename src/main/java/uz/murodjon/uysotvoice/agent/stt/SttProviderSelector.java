package uz.murodjon.uysotvoice.agent.stt;

import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Picks the {@link SttProvider} one call recognizes with (PROJECT.md §2.4). Every
 * implemented provider is registered here; which one a call uses comes from its
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
        if (providers.isEmpty()) {
            throw new IllegalStateException("voice-agent.stt.provider='" + configured
                    + "' selected no STT provider — set it to an implemented id (google, yandex, aisha)");
        }
        if (configured == null || configured.isBlank()) {
            throw new IllegalStateException("voice-agent.stt.provider is not set — set it to one of "
                    + providers.stream().map(SttProvider::name).toList());
        }
        for (SttProvider provider : providers) {
            if (provider.name().equalsIgnoreCase(configured)) {
                return provider;
            }
        }
        throw new IllegalStateException("voice-agent.stt.provider='" + configured
                + "' matches no STT provider — available: " + providers.stream().map(SttProvider::name).toList());
    }

    @PostConstruct
    void logSelection() {
        log.info("STT providers registered: {} (default {})", byName.keySet(), defaultProvider.name());
    }

    /** Every provider id this build can be set to — what the settings screen offers. */
    public List<String> names() {
        return byName.values().stream().map(SttProvider::name).toList();
    }

    /** Whether {@code providerName} is implemented in this build — the check behind a {@code PUT}. */
    public boolean exists(String providerName) {
        return providerName != null && byName.containsKey(providerName.toLowerCase());
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
        SttProvider provider = byName.get(providerName.toLowerCase());
        if (provider == null) {
            log.warn("STT provider '{}' is not implemented in this build — using {}",
                    providerName, defaultProvider.name());
            return defaultProvider;
        }
        return provider;
    }
}
