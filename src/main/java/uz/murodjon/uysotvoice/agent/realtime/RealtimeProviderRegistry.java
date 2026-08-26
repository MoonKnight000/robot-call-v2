package uz.murodjon.uysotvoice.agent.realtime;

import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Every {@link RealtimeProvider} this build has, looked up by id (PROJECT.md §2.4).
 * Which one a call uses comes from its company's {@code engine_config}, with
 * {@code voice-agent.realtime.provider} as the default.
 *
 * <p>Empty is a valid state, and the reason this is a registry rather than a selector
 * like {@code SttProviderSelector}: a deployment with no realtime engine wired is
 * expected, and must boot. What it must <em>not</em> do is let a company select a mode
 * its calls cannot run — {@code EngineConfigService} reads {@link #names()} for exactly
 * that check.
 */
@Component
public class RealtimeProviderRegistry {

    private static final Logger log = LoggerFactory.getLogger(RealtimeProviderRegistry.class);

    private final Map<String, RealtimeProvider> byName = new LinkedHashMap<>();
    private final RealtimeProvider defaultProvider;
    private final boolean enabled;

    /**
     * Takes an {@link ObjectProvider} rather than a plain {@code List}: a required
     * {@code List<RealtimeProvider>} parameter fails to resolve when nothing implements
     * the interface, which would make a build with no realtime engine — the normal case
     * today — refuse to start.
     */
    public RealtimeProviderRegistry(ObjectProvider<RealtimeProvider> providers, RealtimeProperties props) {
        this.enabled = props.enabled();
        if (enabled) {
            providers.orderedStream().forEach(provider -> byName.put(provider.name().toLowerCase(), provider));
        }
        this.defaultProvider = chooseDefault(props.provider());
    }

    /**
     * The configured default, if it names a registered engine. A default naming an
     * engine this build does not have is a misconfiguration, but not a fatal one: with
     * nothing registered there is no realtime mode to break, and the settings screen
     * already refuses to offer one.
     */
    private RealtimeProvider chooseDefault(String configured) {
        if (configured == null || configured.isBlank()) {
            return null;
        }
        RealtimeProvider provider = byName.get(configured.toLowerCase());
        if (provider == null && !byName.isEmpty()) {
            log.warn("voice-agent.realtime.provider='{}' matches no realtime engine — available: {}",
                    configured, byName.keySet());
        }
        return provider;
    }

    @PostConstruct
    void logRegistration() {
        if (!enabled) {
            log.info("Realtime engines disabled (voice-agent.realtime.enabled=false)");
        } else if (byName.isEmpty()) {
            log.info("No realtime engine registered — REALTIME mode is unavailable");
        } else {
            log.info("Realtime engines registered: {} (default {})", byName.keySet(),
                    defaultProvider != null ? defaultProvider.name() : "none");
        }
    }

    /** Every engine id a company may be set to — empty when REALTIME cannot be selected at all. */
    public List<String> names() {
        return byName.values().stream().map(RealtimeProvider::name).toList();
    }

    /** Whether {@code providerName} is implemented and enabled in this build. */
    public boolean exists(String providerName) {
        return providerName != null && byName.containsKey(providerName.toLowerCase());
    }

    /** Whether any engine could serve a call at all — what gates selecting the mode. */
    public boolean isEmpty() {
        return byName.isEmpty();
    }

    /**
     * The engine for one call: the company's chosen id, or the configured default when
     * it chose none.
     *
     * @return {@code null} when neither resolves — the caller has no realtime engine and
     *         must not start the call on one
     */
    public RealtimeProvider findForCall(String providerName) {
        if (providerName == null || providerName.isBlank()) {
            return defaultProvider;
        }
        RealtimeProvider provider = byName.get(providerName.toLowerCase());
        if (provider == null) {
            log.warn("Realtime engine '{}' is not available in this build — falling back to {}",
                    providerName, defaultProvider != null ? defaultProvider.name() : "none");
            return defaultProvider;
        }
        return provider;
    }
}
