package uz.murodjon.uysotvoice.report.dto;

import java.time.Instant;
import java.util.Map;

/**
 * "Shu raqamga tushgan qo'ng'iroqlar statistikasi" (§10.9 drawer) — everything a call
 * matched to one {@code inbound_route} has actually done, mirroring {@link
 * CampaignStats}' shape for the same question asked of an inbound number instead of a
 * campaign.
 *
 * @param inboundRouteId the route
 * @param didNumber      its dialled number, so a report is readable on its own
 * @param totalCalls     every call this route has ever matched
 * @param answeredCalls  attempts that reached a conversation (measured duration)
 * @param answerRate     answeredCalls / totalCalls (0..1), 0 with no calls yet
 * @param avgDurationSec mean length of an answered call, or null with nothing to average
 * @param lastCallAt     when this route was last dialled into, or null if never
 * @param dispositions   finished attempts by disposition
 * @param statsAvailableFrom the earliest {@code call_attempt.started_at} in this company
 *                       with a non-null {@code inbound_route_id} — i.e. when inbound-route
 *                       attribution started being recorded at all (backend-uchun-talablar.md
 *                       §8). {@code null} means no inbound call has ever been attributed to
 *                       a route yet. Calls before this timestamp are invisible to every
 *                       {@code inbound_route}'s stats, not just this one — the frontend
 *                       should treat totals as "since {@code statsAvailableFrom}", not
 *                       "all-time", when this is non-null and recent
 */
public record InboundRouteStats(
        long inboundRouteId,
        String didNumber,
        long totalCalls,
        long answeredCalls,
        double answerRate,
        Double avgDurationSec,
        Instant lastCallAt,
        Map<String, Long> dispositions,
        Instant statsAvailableFrom
) {
}
