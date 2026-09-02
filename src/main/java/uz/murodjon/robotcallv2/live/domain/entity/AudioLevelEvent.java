package uz.murodjon.robotcallv2.live.domain.entity;

/**
 * A caller's instantaneous audio level, for the two-line real-time waveform on a live
 * call's card (§11.8).
 *
 * @param channelId the live call this level belongs to
 * @param level     RMS level normalized to {@code [0, 1]}
 */
public record AudioLevelEvent(String channelId, float level) {
}

