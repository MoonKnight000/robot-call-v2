package uz.murodjon.uysotvoice.live.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Live SSE channel settings. Bound from {@code voice-agent.live.*} (§0.7).
 *
 * @param audioLevelEnabled compute and publish a per-call RMS level on every RTP
 *                          frame (§11.8). Off by default: it runs on the RTP consumer
 *                          thread of every active call, so a deployment with no live
 *                          waveform UI open should not pay for it
 * @param kpiPollMs         how often the KPI/live-call-list snapshot is taken and
 *                          diffed against the last one published
 */
@ConfigurationProperties(prefix = "voice-agent.live")
public record LiveProperties(
        boolean audioLevelEnabled,
        int kpiPollMs
) {
}
