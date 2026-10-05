package uz.murodjon.robotcallv2.agent.tts;

import org.springframework.stereotype.Component;
import uz.murodjon.robotcallv2.agent.provider.NamedProviderSelector;

import java.util.List;

/**
 * Resolves the {@link TtsProvider} to speak an utterance with. Follows the same pattern
 * as {@link uz.murodjon.robotcallv2.agent.stt.SttProviderSelector} — multi-tenant
 * deployments let each agent pick its own provider ({@code ai_agent.tts_provider}), and the
 * app-wide {@code voice-agent.tts.provider} is the default when none is picked.
 */
@Component
public class TtsProviderSelector extends NamedProviderSelector<TtsProvider> {

    public TtsProviderSelector(List<TtsProvider> providers, TtsProperties ttsProperties) {
        super("TTS", providers, ttsProperties.provider());
    }

    /**
     * The provider named {@code providerName}, or {@code null} if this build has none by
     * that name — for a caller that already has a fallback of its own ({@link TtsRouter}
     * routing a chosen voice to its own provider) and does not want {@link #findForCall}'s
     * warning-and-default behaviour.
     */
    public TtsProvider tryFind(String providerName) {
        return providerName == null ? null : lookup(providerName);
    }
}
