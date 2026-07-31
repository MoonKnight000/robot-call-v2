package uz.murodjon.uysotvoice.campaign.dto;

import uz.murodjon.uysotvoice.shared.csv.CsvRowError;

import java.util.List;

/**
 * Outcome of parsing a target file: what can be inserted, and what was wrong.
 *
 * @param targets        rows that parsed cleanly
 * @param errors         rows that did not, with the reason
 * @param unknownColumns header columns the importer ignored, so a misspelled header is
 *                       visible rather than silently dropping a whole column
 */
public record TargetCsvParseResult(
        List<ParsedTarget> targets,
        List<CsvRowError> errors,
        List<String> unknownColumns
) {
}
