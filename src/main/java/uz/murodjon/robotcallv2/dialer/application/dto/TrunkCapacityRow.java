package uz.murodjon.robotcallv2.dialer.application.dto;

import java.time.Instant;

/**
 * How many calls one of the company's SIP trunks carries at once ({@code GET /api/concurrency/trunks}).
 *
 * <p>SIP has no way to ask a provider for its channel count, so besides the limit the
 * company set, this shows what the dialer has learned from the calls themselves:
 * {@code peakConnected} is the most calls the trunk was seen carrying while one went
 * through — proven to work — and {@code congestionLimit} is how many were up when the
 * provider last refused one more for want of a channel.
 *
 * @param maxChannels          the limit set on the trunk; null = none
 * @param active               dialer calls over it right now
 * @param peakConnected        the most dialer calls seen up at once while one connected; 0 = none yet
 * @param congestionLimit      calls up when the provider last refused one with "no channel"; null = never
 * @param lastCongestionAt     when that was
 * @param suggestedMaxChannels what to set {@code maxChannels} to; null until a refusal has been seen
 */
public record TrunkCapacityRow(
        long sipTrunkId,
        String name,
        Integer maxChannels,
        int active,
        int peakConnected,
        Integer congestionLimit,
        Instant lastCongestionAt,
        Integer suggestedMaxChannels
) {
}
