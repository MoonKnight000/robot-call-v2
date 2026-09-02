package uz.murodjon.robotcallv2.campaign.application.dto;

import uz.murodjon.robotcallv2.shared.csv.CsvRowError;

import java.util.List;

/**
 * {@code POST /api/campaigns/{id}/targets/csv/preview} response (§10.6 "ustunni
 * moslashtirish" wizard step) — parses the file and reports how it would be loaded,
 * without inserting anything.
 *
 * @param columns       each CSV header matched against a known field, or unmatched
 * @param sampleRows    first rows that parsed cleanly, for an on-screen preview
 * @param totalRows     how many data rows parsed cleanly in total (may exceed {@code sampleRows.size()})
 * @param errors        rows that failed to parse, with the reason
 * @param unknownColumns headers the importer does not recognize (also reflected in {@code columns})
 */
public record TargetCsvPreview(
        List<CsvColumnMapping> columns,
        List<ParsedTarget> sampleRows,
        int totalRows,
        List<CsvRowError> errors,
        List<String> unknownColumns
) {
}
