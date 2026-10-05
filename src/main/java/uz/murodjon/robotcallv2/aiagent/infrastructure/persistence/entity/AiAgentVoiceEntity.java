package uz.murodjon.robotcallv2.aiagent.infrastructure.persistence.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/**
 * The ai_agent columns behind {@code AiAgentVoice}. The per-language voices are a join table,
 * so they stay on {@link AiAgentEntity}.
 */
@Embeddable
public class AiAgentVoiceEntity {

    @Column(name = "tts_voice")
    private String ttsVoice;

    @Column(name = "voice_speed")
    private Double voiceSpeed = 1.0;

    @Column(name = "voice_stability")
    private Double voiceStability = 0.5;

    @Column(name = "voice_similarity_boost")
    private Double voiceSimilarityBoost = 0.75;

    /** Language code → speed as JSON (NEXT-PLAN A.7); null when every language uses voice_speed. */
    @Column(name = "voice_speed_per_language")
    @JdbcTypeCode(SqlTypes.JSON)
    private String voiceSpeedPerLanguage;

    @Column(name = "emotion_adaptive_voice", nullable = false)
    private boolean emotionAdaptiveVoice;

    protected AiAgentVoiceEntity() {
    }

    public AiAgentVoiceEntity(String ttsVoice, Double voiceSpeed, Double voiceStability,
                              Double voiceSimilarityBoost, String voiceSpeedPerLanguage,
                              boolean emotionAdaptiveVoice) {
        this.ttsVoice = ttsVoice;
        this.voiceSpeed = voiceSpeed;
        this.voiceStability = voiceStability;
        this.voiceSimilarityBoost = voiceSimilarityBoost;
        this.voiceSpeedPerLanguage = voiceSpeedPerLanguage;
        this.emotionAdaptiveVoice = emotionAdaptiveVoice;
    }

    public String getTtsVoice() {
        return ttsVoice;
    }

    public Double getVoiceSpeed() {
        return voiceSpeed;
    }

    public Double getVoiceStability() {
        return voiceStability;
    }

    public Double getVoiceSimilarityBoost() {
        return voiceSimilarityBoost;
    }

    public String getVoiceSpeedPerLanguage() {
        return voiceSpeedPerLanguage;
    }

    public boolean isEmotionAdaptiveVoice() {
        return emotionAdaptiveVoice;
    }
}
