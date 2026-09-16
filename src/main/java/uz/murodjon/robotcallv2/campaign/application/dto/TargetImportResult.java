package uz.murodjon.robotcallv2.campaign.application.dto;

import uz.murodjon.robotcallv2.shared.csv.CsvRowError;

import java.util.List;

/**
 * Outcome of a target CSV import.
 *
 * @param campaignId        campaign the targets were loaded into
 * @param added             how many targets were inserted
 * @param targetIds         ids of those targets
 * @param skippedDuplicates rows whose number the campaign already has queued — the same
 *                          file uploaded twice inserts nothing the second time
 * @param skippedDoNotCall  rows inserted with the do-not-call mark because the number is on
 *                          the company's list: counted, visible, never dialled
 * @param errors            rows that were rejected, with the reason
 * @param unknownColumns    headers that were ignored — usually a typo in a header, which is
 *                          worth surfacing because the facts in that column silently never
 *                          reach the agent
 */
public record TargetImportResult(
        long campaignId,
        int added,
        List<Long> targetIds,
        int skippedDuplicates,
        int skippedDoNotCall,
        List<CsvRowError> errors,
        List<String> unknownColumns
) {
}
