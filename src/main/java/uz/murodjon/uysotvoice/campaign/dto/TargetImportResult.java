package uz.murodjon.uysotvoice.campaign.dto;

import uz.murodjon.uysotvoice.shared.csv.CsvRowError;

import java.util.List;

/**
 * Outcome of a target CSV import.
 *
 * @param campaignId     campaign the targets were loaded into
 * @param added          how many targets were inserted
 * @param targetIds      ids of those targets
 * @param errors         rows that were rejected, with the reason
 * @param unknownColumns headers that were ignored — usually a typo in a header, which is
 *                       worth surfacing because the facts in that column silently never
 *                       reach the agent
 */
public record TargetImportResult(
        long campaignId,
        int added,
        List<Long> targetIds,
        List<CsvRowError> errors,
        List<String> unknownColumns
) {
}
