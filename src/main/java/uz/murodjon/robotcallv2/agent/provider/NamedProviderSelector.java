package uz.murodjon.robotcallv2.agent.provider;

import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Resolves the provider of one kind (STT, TTS) a call uses. Provider choice lives on the
 * agent, and the deployment's configured provider is the default for an agent that has not
 * chosen; with no usable default the first registered provider is used.
 *
 * <p>{@code google} resolves to {@code gemini} whenever a Gemini provider is registered.
 */
public abstract class NamedProviderSelector<T extends NamedProvider> {

    private final Logger log = LoggerFactory.getLogger(getClass());
    private static final String GOOGLE = "google";
    private static final String GEMINI = "gemini";

    private final String kind;
    private final Map<String, T> byName = new LinkedHashMap<>();
    private final T defaultProvider;

    protected NamedProviderSelector(String kind, List<T> providers, String configuredDefault) {
        this.kind = kind;
        for (T provider : providers) {
            byName.put(provider.name().toLowerCase(), provider);
        }
        this.defaultProvider = chooseDefault(configuredDefault);
    }

    private T chooseDefault(String configured) {
        if (byName.isEmpty()) {
            return null;
        }
        T provider = configured != null && !configured.isBlank() ? lookup(configured) : null;
        return provider != null ? provider : byName.values().iterator().next();
    }

    @PostConstruct
    void logSelection() {
        log.info("{} providers registered: {} (default {})", kind, byName.keySet(),
                defaultProvider != null ? defaultProvider.name() : "none");
    }

    /** The provider registered as {@code providerName} (alias applied), or null when there is none. */
    protected T lookup(String providerName) {
        String key = providerName.toLowerCase();
        if (GOOGLE.equals(key) && byName.containsKey(GEMINI)) {
            return byName.get(GEMINI);
        }
        return byName.get(key);
    }

    /** Every provider id this build can be set to — what the settings screen offers. */
    public List<String> names() {
        return byName.values().stream().map(NamedProvider::name).toList();
    }

    /** Whether {@code providerName} is implemented in this build — the check behind a {@code PUT}. */
    public boolean exists(String providerName) {
        return providerName != null && lookup(providerName) != null;
    }

    /**
     * The provider for one call: the agent's chosen id, or the configured default when it
     * chose none.
     *
     * <p>An id that no longer resolves falls back to the default rather than failing the
     * call — the setting was validated when it was saved, so getting here means the
     * provider left the build afterwards, and a live call is the worst place to discover
     * that.
     */
    public T findForCall(String providerName) {
        if (providerName == null || providerName.isBlank()) {
            return defaultProvider;
        }
        T provider = lookup(providerName);
        if (provider == null) {
            log.warn("{} provider '{}' is not implemented in this build — using {}",
                    kind, providerName, defaultProvider != null ? defaultProvider.name() : "none");
            return defaultProvider;
        }
        return provider;
    }

    /**
     * Another provider to use when {@code failed} has stopped working during a call, or
     * {@code null} when this build has only the one.
     *
     * <p>Any registered provider will do: a provider is only a bean when its API key is
     * configured ({@code @ConditionalOnExpression} on each), so what is here is what this
     * deployment actually paid for. Language is not checked — a recognizer or voice with a
     * worse accent is still better than a deaf or mute call.
     */
    public T findFallback(T failed) {
        for (T provider : byName.values()) {
            if (provider != failed && (failed == null || !provider.name().equalsIgnoreCase(failed.name()))) {
                return provider;
            }
        }
        return null;
    }
}
