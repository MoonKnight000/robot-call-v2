package uz.murodjon.robotcallv2.aiagent.application.dto;

import uz.murodjon.robotcallv2.aiagent.domain.entity.AiAgentVoice;
import uz.murodjon.robotcallv2.aiagent.domain.enums.SpeakingWarmth;

import java.util.Map;

/**
 * {@link AiAgentVoice} as the agent endpoints read and write it. On a write a null field
 * keeps what the agent has (or the create default).
 *
 * @param defaultVoice a tts_voice id; {@code GET /api/ai-agents/voices} lists them
 */
public record AgentVoiceDto(
        String defaultVoice,
        Double speed,
        Double stability,
        Double similarityBoost,
        Map<String, String> perLanguage,
        Map<String, Double> perLanguageSpeed,
        Boolean emotionAdaptive,
        SpeakingWarmth warmth
) {

    public static AgentVoiceDto of(AiAgentVoice voice) {
        return new AgentVoiceDto(voice.defaultVoice(), voice.speed(), voice.stability(), voice.similarityBoost(),
                voice.perLanguage(), voice.perLanguageSpeed(), voice.emotionAdaptive(), voice.warmth());
    }
}
