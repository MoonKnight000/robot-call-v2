package uz.murodjon.robotcallv2.aiagent.infrastructure.persistence.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import uz.murodjon.robotcallv2.aiagent.domain.enums.SpeakingWarmth;

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

    /** Language code → speed as JSON (NEXT-PLAN A.7); null when every language uses voice_speed. */
    @Column(name = "voice_speed_per_language")
    @JdbcTypeCode(SqlTypes.JSON)
    private String voiceSpeedPerLanguage;

    @Column(name = "emotion_adaptive_voice", nullable = false)
    private boolean emotionAdaptiveVoice;

    @Enumerated(EnumType.STRING)
    @Column(name = "speaking_warmth", nullable = false, length = 16)
    private SpeakingWarmth speakingWarmth = SpeakingWarmth.BALANCED;

    protected AiAgentVoiceEntity() {
    }

    public AiAgentVoiceEntity(String ttsVoice, Double voiceSpeed, String voiceSpeedPerLanguage,
                              boolean emotionAdaptiveVoice, SpeakingWarmth speakingWarmth) {
        this.ttsVoice = ttsVoice;
        this.voiceSpeed = voiceSpeed;
        this.voiceSpeedPerLanguage = voiceSpeedPerLanguage;
        this.emotionAdaptiveVoice = emotionAdaptiveVoice;
        this.speakingWarmth = speakingWarmth;
    }

    public String getTtsVoice() {
        return ttsVoice;
    }

    public Double getVoiceSpeed() {
        return voiceSpeed;
    }

    public String getVoiceSpeedPerLanguage() {
        return voiceSpeedPerLanguage;
    }

    public boolean isEmotionAdaptiveVoice() {
        return emotionAdaptiveVoice;
    }

    public SpeakingWarmth getSpeakingWarmth() {
        return speakingWarmth;
    }
}
