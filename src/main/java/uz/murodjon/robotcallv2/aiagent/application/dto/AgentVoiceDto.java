package uz.murodjon.robotcallv2.aiagent.application.dto;

import uz.murodjon.robotcallv2.aiagent.domain.entity.AiAgentVoice;
import uz.murodjon.robotcallv2.aiagent.domain.enums.SpeakingWarmth;
import uz.murodjon.robotcallv2.aiagent.domain.enums.VoiceEmotion;

import java.util.Map;

/**
 * {@link AiAgentVoice} as the agent endpoints read and write it. On a write a null field
 * keeps what the agent has (or the create default).
 *
 * @param defaultVoice  a tts_voice id; {@code GET /api/ai-agents/voices} lists them
 * @param fallbackVoice a tts_voice id spoken when the agent's TTS provider fails; null on a write keeps it
 *                      ({@code clearFields: FALLBACK_VOICE} removes it), null on a read — automatic fallback
 */
public record AgentVoiceDto(
        String defaultVoice,
        Double speed,
        Map<String, String> perLanguage,
        Map<String, Double> perLanguageSpeed,
        Boolean emotionAdaptive,
        SpeakingWarmth warmth,
        VoiceEmotion emotion,
        String fallbackVoice
) {

    public static AgentVoiceDto of(AiAgentVoice voice) {
        return new AgentVoiceDto(voice.defaultVoice(), voice.speed(), voice.perLanguage(), voice.perLanguageSpeed(), voice.emotionAdaptive(), voice.warmth(), voice.emotion(), voice.fallbackVoice());
    }
}
