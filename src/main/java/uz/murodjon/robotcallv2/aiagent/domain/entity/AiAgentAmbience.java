package uz.murodjon.robotcallv2.aiagent.domain.entity;

import uz.murodjon.robotcallv2.aiagent.domain.enums.AmbientSound;
import uz.murodjon.robotcallv2.aiagent.domain.enums.NoiseCancellationMode;

/**
 * What is on the line besides the words: what the caller hears behind the agent, and what
 * is taken out of what the caller sends.
 *
 * <p>The background sound is not decoration. A synthesised voice on a perfectly silent
 * line reads as a recording, and callers hang up on recordings; a room behind it reads as
 * a person at a desk. The thinking sound covers the gap while a tool call or a slow model
 * turn is in flight, for the same reason.
 *
 * @param background      the room behind the agent for the whole call
 * @param backgroundVolume       1.0 is the sample's own level
 * @param backgroundFadeInSeconds how long the room takes to come up at answer
 * @param thinking        played while a turn is being prepared; {@link AmbientSound#OFF} to stay silent
 * @param thinkingVolume  1.0 is the sample's own level
 * @param noiseCancellationEnabled whether the caller's own audio is cleaned before recognition
 * @param noiseCancellationMode    how aggressively; never null
 */
public record AiAgentAmbience(
        AmbientSound background,
        Double backgroundVolume,
        Double backgroundFadeInSeconds,
        AmbientSound thinking,
        Double thinkingVolume,
        boolean noiseCancellationEnabled,
        NoiseCancellationMode noiseCancellationMode
) {
    public AiAgentAmbience {
        if (background == null) {
            background = AmbientSound.OFFICE;
        }
        if (backgroundVolume == null) {
            backgroundVolume = 1.0;
        }
        if (backgroundFadeInSeconds == null) {
            backgroundFadeInSeconds = 1.5;
        }
        if (thinking == null) {
            thinking = AmbientSound.OFF;
        }
        if (thinkingVolume == null) {
            thinkingVolume = 1.0;
        }
        if (noiseCancellationMode == null) {
            noiseCancellationMode = NoiseCancellationMode.BACKGROUND_NOISE_SUPPRESSION;
        }
    }
}
