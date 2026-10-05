package uz.murodjon.robotcallv2.agent.stt;

import org.springframework.stereotype.Component;
import uz.murodjon.robotcallv2.agent.provider.NamedProviderSelector;

import java.util.List;

/**
 * Resolves the {@link SttProvider} to transcribe a call with. Provider choice lives on
 * the agent ({@code ai_agent.stt_provider}), and {@code voice-agent.stt.provider} is the
 * default for an agent that has not chosen — a misconfigured default fails the app at startup
 * here instead of silently leaving speech recognition off until the first call
 * discovers it missing.
 *
 * <p>This used to resolve a single provider for the whole run. It no longer can: which
 * vendor recognizes a company's Uzbek is that company's setting, and two tenants on one
 * instance may well answer differently.
 *
 * <p>{@link #findFallback} matters more here than for TTS: synthesis failing costs one
 * sentence, recognition failing costs the rest of the call. Without it
 * {@link SttStreamBridge#reopen()} could only reopen the provider that had just failed, so
 * a vendor that was down — or up and silently returning nothing — left the bot listening
 * to a caller it could no longer hear.
 */
@Component
public class SttProviderSelector extends NamedProviderSelector<SttProvider> {

    public SttProviderSelector(List<SttProvider> providers, SttProperties sttProperties) {
        super("STT", providers, sttProperties.provider());
    }
}
