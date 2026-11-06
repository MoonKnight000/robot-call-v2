package uz.murodjon.robotcallv2.aiagent.domain.entity;

import uz.murodjon.robotcallv2.aiagent.domain.enums.SpeakingWarmth;
import uz.murodjon.robotcallv2.aiagent.domain.enums.VoiceEmotion;

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
 * @param perLanguage    language code → tts_voice id; never null
 * @param perLanguageSpeed language code → speed for calls in that language; never null.
 *                        A language without an entry speaks at {@code speed}
 * @param emotionAdaptive whether the delivery follows the detected sentiment of the call
 * @param warmth          how much warmth the wording carries; never null, {@link SpeakingWarmth#BALANCED}
 *                        unless said otherwise
 * @param emotion         the mood the agent speaks in when no stage and no caller sentiment asks for
 *                        another; never null, {@link VoiceEmotion#NEUTRAL} unless said otherwise
 * @param fallbackVoice   the tts_voice id that speaks when the agent's own TTS provider fails mid-call;
 *                        null lets the router pick any other provider for the language, with its default voice
 */
public record AiAgentVoice(
        String defaultVoice,
        Double speed,
        Map<String, String> perLanguage,
        Map<String, Double> perLanguageSpeed,
        boolean emotionAdaptive,
        SpeakingWarmth warmth,
        VoiceEmotion emotion,
        String fallbackVoice
) {
    public AiAgentVoice {
        if (speed == null) {
            speed = 1.0;
        }
        perLanguage = perLanguage == null ? Map.of() : Map.copyOf(perLanguage);
        perLanguageSpeed = perLanguageSpeed == null ? Map.of() : Map.copyOf(perLanguageSpeed);
        if (warmth == null) {
            warmth = SpeakingWarmth.BALANCED;
        }
        if (emotion == null) {
            emotion = VoiceEmotion.NEUTRAL;
        }
    }

    public AiAgentVoice(String defaultVoice, Double speed,
                        Map<String, String> perLanguage, Map<String, Double> perLanguageSpeed,
                        boolean emotionAdaptive) {
        this(defaultVoice, speed, perLanguage, perLanguageSpeed, emotionAdaptive, null, null, null);
    }

    /** The same voice, following the call's sentiment or not. */
    public AiAgentVoice withEmotionAdaptive(boolean newEmotionAdaptive) {
        return new AiAgentVoice(defaultVoice, speed, perLanguage, perLanguageSpeed, newEmotionAdaptive, warmth, emotion, fallbackVoice);
    }

    /** The same voice, its wording as warm as {@code newWarmth}. */
    public AiAgentVoice withWarmth(SpeakingWarmth newWarmth) {
        return new AiAgentVoice(defaultVoice, speed, perLanguage, perLanguageSpeed, emotionAdaptive, newWarmth, emotion, fallbackVoice);
    }

    /** The same voice, speaking in {@code newEmotion} by default. */
    public AiAgentVoice withEmotion(VoiceEmotion newEmotion) {
        return new AiAgentVoice(defaultVoice, speed, perLanguage, perLanguageSpeed, emotionAdaptive, warmth, newEmotion, fallbackVoice);
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

    /** The pace a call in {@code language} speaks at; the agent's own {@code speed} otherwise. */
    public double speedFor(String language) {
        if (language != null) {
            Double own = perLanguageSpeed.get(language);
            if (own != null) {
                return own;
            }
        }
        return speed;
    }
}
