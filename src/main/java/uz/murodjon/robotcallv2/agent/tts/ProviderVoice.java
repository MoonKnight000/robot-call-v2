package uz.murodjon.robotcallv2.agent.tts;

import java.util.Set;

/**
 * One voice as a provider's own list API describes it ({@link TtsProvider#listVoices()}).
 *
 * @param name      provider-side voice id or name, sent with synthesis as is
 * @param label     human-readable name for the agent form
 * @param languages ISO 639-1 codes the voice is offered for ({@code uz}, {@code ru});
 *                  empty when the model speaks every language with any voice
 * @param gender    the vendor's own word for the voice's gender ("female", "feminine"), or null
 * @param age       the vendor's own word for the voice's age ("young", "middle_aged"), or null
 */
public record ProviderVoice(
        String name,
        String label,
        Set<String> languages,
        String gender,
        String age
) {
}
