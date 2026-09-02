package uz.murodjon.robotcallv2.report.domain.entity;

import uz.murodjon.robotcallv2.campaign.domain.enums.CampaignStatus;

import java.util.Map;

/**
 * What a campaign has actually achieved (PROJECT.md §10 Bosqich 12).
 *
 * <p>The Micrometer metrics answer "is the system healthy"; this answers "is the campaign
 * working", which is a different question and the one the operator running it asks. Before
 * this, the only way to see a campaign's outcome was to read the logs or query Postgres by
 * hand.
 *
 * @param campaignId      the campaign
 * @param name            its name, so a report is readable on its own
 * @param status          the campaign's lifecycle status
 * @param targetsByStatus how many targets sit in each lifecycle status
 * @param dispositions    finished attempts by disposition — the §10 "disposition taqsimoti"
 * @param answeredCalls   attempts that reached a conversation
 * @param promiseRate     share of finished attempts that ended in PROMISE_TO_PAY (0..1),
 *                        the number the campaign is judged on
 * @param avgDurationSec  mean length of a finished call, or null with nothing to average
 * @param escalatedCalls  calls handed to a human operator (§11.6)
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
        long escalatedCalls
) {
}

