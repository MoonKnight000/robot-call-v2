package uz.murodjon.robotcallv2.report.domain.entity;

import uz.murodjon.robotcallv2.campaign.domain.enums.CampaignStatus;
import uz.murodjon.robotcallv2.shared.dialog.Disposition;

import java.util.Arrays;
import java.util.Map;

/**
 * What a campaign has actually achieved (PROJECT.md §10 Bosqich 12).
 *
 * <p>The Micrometer metrics answer "is the system healthy"; this answers "is the campaign
 * working", which is a different question and the one the operator running it asks. Before
 * this, the only way to see a campaign's outcome was to read the logs or query Postgres by
 * hand.
 *
 * @param campaignId           the campaign
 * @param name                 its name, so a report is readable on its own
 * @param status               the campaign's lifecycle status
 * @param targetsByStatus      how many targets sit in each lifecycle status
 * @param dispositions         finished attempts by disposition — the §10 "disposition taqsimoti"
 * @param answeredCalls        finished attempts (kept under its historical name for the panel)
 * @param promiseRate          share of finished attempts that ended in PROMISE_TO_PAY (0..1),
 *                             the number the campaign is judged on
 * @param avgDurationSec       mean length of a finished call, or null with nothing to average
 * @param escalatedCalls       calls handed to a human operator (§11.6)
 * @param connectedCalls       attempts somebody picked up ({@code answered_at} set)
 * @param asr                  answer-seizure ratio: connected / finished attempts (0..1) — a
 *                             telephony figure; low with a healthy list means the trunk or
 *                             the caller ID, not the script
 * @param contactRate          share of the targets attempted so far that were reached at
 *                             least once (0..1) — what the list itself is worth
 * @param connectedByAttemptNo how many targets were first reached on their 1st, 2nd, 3rd…
 *                             attempt — whether the third try still pays for itself
 * @param spentUzs             what the campaign's settled calls were charged in total
 * @param costPerConnectedUzs  spend per connected call, or null with none connected
 * @param costPerConversionUzs spend per PROMISE_TO_PAY / GOAL_REACHED / COMPLETED, or null with none
 */
public record CampaignStats(
        long campaignId,
        String name,
        CampaignStatus status,
        Map<String, Long> targetsByStatus,
        Map<String, Long> dispositions,
        long answeredCalls,
        double promiseRate,
        Double avgDurationSec,
        long escalatedCalls,
        long connectedCalls,
        double asr,
        double contactRate,
        Map<Integer, Long> connectedByAttemptNo,
        long spentUzs,
        Long costPerConnectedUzs,
        Long costPerConversionUzs
) {

    /**
     * These figures with the campaign's settled spend and the two unit costs derived from
     * it. The spend comes from billing, which owns the ledger; a conversion is a successful
     * call as {@link Disposition#isConversion()} defines it.
     */
    public CampaignStats withSpend(long spentUzs) {
        long conversions = Arrays.stream(Disposition.values())
                .filter(Disposition::isConversion)
                .mapToLong(disposition -> dispositions.getOrDefault(disposition.name(), 0L))
                .sum();
        return new CampaignStats(campaignId, name, status, targetsByStatus, dispositions, answeredCalls,
                promiseRate, avgDurationSec, escalatedCalls, connectedCalls, asr, contactRate,
                connectedByAttemptNo, spentUzs,
                connectedCalls == 0 ? null : spentUzs / connectedCalls,
                conversions == 0 ? null : spentUzs / conversions);
    }
}
