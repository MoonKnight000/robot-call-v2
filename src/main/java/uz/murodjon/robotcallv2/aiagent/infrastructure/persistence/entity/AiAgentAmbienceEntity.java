package uz.murodjon.robotcallv2.aiagent.infrastructure.persistence.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import uz.murodjon.robotcallv2.aiagent.domain.enums.AmbientSound;
import uz.murodjon.robotcallv2.aiagent.domain.enums.NoiseCancellationMode;

/** The ai_agent columns behind {@code AiAgentAmbience}: what the caller hears besides the voice. */
@Embeddable
public class AiAgentAmbienceEntity {

    @Enumerated(EnumType.STRING)
    @Column(name = "ambient_sound", nullable = false)
    private AmbientSound ambientSound = AmbientSound.OFFICE;

    @Column(name = "ambient_sound_volume", nullable = false)
    private double ambientSoundVolume = 1.0;

    @Column(name = "ambient_sound_fade_in_seconds", nullable = false)
    private double ambientSoundFadeInSeconds = 1.5;

    @Enumerated(EnumType.STRING)
    @Column(name = "thinking_sound", nullable = false, length = 50)
    private AmbientSound thinkingSound = AmbientSound.OFF;

    @Column(name = "thinking_sound_volume", nullable = false)
    private double thinkingSoundVolume = 1.0;

    @Column(name = "noise_cancellation_enabled", nullable = false)
    private boolean noiseCancellationEnabled = true;

    @Enumerated(EnumType.STRING)
    @Column(name = "noise_cancellation_mode", nullable = false, length = 50)
    private NoiseCancellationMode noiseCancellationMode = NoiseCancellationMode.BACKGROUND_NOISE_SUPPRESSION;

    protected AiAgentAmbienceEntity() {
    }

    public AiAgentAmbienceEntity(AmbientSound ambientSound, double ambientSoundVolume,
                                 double ambientSoundFadeInSeconds, AmbientSound thinkingSound,
                                 double thinkingSoundVolume, boolean noiseCancellationEnabled,
                                 NoiseCancellationMode noiseCancellationMode) {
        this.ambientSound = ambientSound != null ? ambientSound : AmbientSound.OFFICE;
        this.ambientSoundVolume = ambientSoundVolume;
        this.ambientSoundFadeInSeconds = ambientSoundFadeInSeconds;
        this.thinkingSound = thinkingSound != null ? thinkingSound : AmbientSound.OFF;
        this.thinkingSoundVolume = thinkingSoundVolume;
        this.noiseCancellationEnabled = noiseCancellationEnabled;
        this.noiseCancellationMode = noiseCancellationMode != null
                ? noiseCancellationMode : NoiseCancellationMode.BACKGROUND_NOISE_SUPPRESSION;
    }

    public AmbientSound getAmbientSound() {
        return ambientSound;
    }

    public double getAmbientSoundVolume() {
        return ambientSoundVolume;
    }

    public double getAmbientSoundFadeInSeconds() {
        return ambientSoundFadeInSeconds;
    }

    public AmbientSound getThinkingSound() {
        return thinkingSound;
    }

    public double getThinkingSoundVolume() {
        return thinkingSoundVolume;
    }

    public boolean isNoiseCancellationEnabled() {
        return noiseCancellationEnabled;
    }

    public NoiseCancellationMode getNoiseCancellationMode() {
        return noiseCancellationMode;
    }
}
