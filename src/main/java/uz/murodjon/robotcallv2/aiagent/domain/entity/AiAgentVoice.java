package uz.murodjon.robotcallv2.aiagent.domain.entity;

import java.util.Map;

/**
 * How the agent sounds.
 *
 * <p>{@code perLanguage} is what lets one agent answer in more than one language without
 * being copied: a ru-RU caller gets the Russian voice, everyone else gets {@link
 * #defaultVoice()}. {@link #forLanguage(String)} is the only correct way to pick between
 * them, and the reason this is a type rather than five loose fields.
 *
 * @param defaultVoice    the tts_voice id used when the call's language has no voice of its own
 * @param speed           1.0 is the voice's own pace
 * @param stability       how little the delivery varies between renderings
 * @param similarityBoost how closely the synthesis is held to the reference voice
 * @param perLanguage     language code → tts_voice id; never null
 * @param emotionAdaptive whether the delivery follows the detected sentiment of the call
 */
public record AiAgentVoice(
        String defaultVoice,
        Double speed,
        Double stability,
        Double similarityBoost,
        Map<String, String> perLanguage,
        boolean emotionAdaptive
) {
    public AiAgentVoice {
        if (speed == null) {
            speed = 1.0;
        }
        if (stability == null) {
            stability = 0.5;
        }
        if (similarityBoost == null) {
            similarityBoost = 0.75;
        }
        perLanguage = perLanguage == null ? Map.of() : Map.copyOf(perLanguage);
    }

    /** The same voice, following the call's sentiment or not. */
    public AiAgentVoice withEmotionAdaptive(boolean newEmotionAdaptive) {
        return new AiAgentVoice(defaultVoice, speed, stability, similarityBoost, perLanguage, newEmotionAdaptive);
    }

    /** The voice a call in {@code language} speaks with; the agent's default otherwise. */
    public String forLanguage(String language) {
        if (language != null) {
            String voice = perLanguage.get(language);
            if (voice != null && !voice.isBlank()) {
                return voice;
            }
        }
        return defaultVoice;
    }
}
