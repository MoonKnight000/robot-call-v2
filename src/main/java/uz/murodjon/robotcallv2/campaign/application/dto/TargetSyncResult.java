package uz.murodjon.robotcallv2.campaign.application.dto;

import uz.murodjon.robotcallv2.shared.csv.CsvRowError;

import java.util.List;

/**
 * Outcome of fetching a campaign's list from its target source.
 *
 * @param fetched           how many rows the endpoint answered with
 * @param added             how many of them became targets
 * @param removed           how many existing targets were cleared first ({@code replaceTargets})
 * @param skippedDuplicates rows whose number was already queued (a sync without
 *                          {@code replaceTargets} never doubles the list)
 * @param skippedDoNotCall  rows stored with the do-not-call mark, never dialled
 * @param errors            rows the endpoint got wrong, with the reason and the row's position
 */
public record TargetSyncResult(
        long campaignId,
        int fetched,
        int added,
        int removed,
        int skippedDuplicates,
        int skippedDoNotCall,
        List<CsvRowError> errors
) {
}
