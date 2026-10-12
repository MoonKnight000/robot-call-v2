package uz.murodjon.robotcallv2.report.domain.entity;

/**
 * How good one campaign's contact list turned out to be over a period, counted per target
 * dialled rather than per attempt — a number retried five times is still one bad row.
 *
 * @param dialledTargets         targets with at least one attempt in the period
 * @param wrongNumberTargets     targets where someone other than the client answered ({@code WRONG_NUMBER})
 * @param carrierRejectedTargets targets the carrier refused to route ({@code CARRIER_REJECTED}) — usually a
 *                               number that does not exist or is malformed
 * @param neverAnsweredTargets   targets none of whose attempts was answered
 * @param badDataRate            ({@code wrongNumberTargets} + {@code carrierRejectedTargets}) / {@code dialledTargets}
 *                               (0..1) — the share of the list that is wrong rather than merely unlucky
 */
public record CampaignDataQuality(long campaignId, String campaignName, long dialledTargets, long wrongNumberTargets,
                                  long carrierRejectedTargets, long neverAnsweredTargets, double badDataRate) {
}
