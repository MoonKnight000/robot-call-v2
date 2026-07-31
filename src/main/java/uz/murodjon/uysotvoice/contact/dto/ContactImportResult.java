package uz.murodjon.uysotvoice.contact.dto;

import uz.murodjon.uysotvoice.shared.csv.CsvRowError;

import java.util.List;

/**
 * Outcome of a contact CSV import (mirrors {@code TargetImportResult}'s shape).
 *
 * @param added          how many contacts were inserted
 * @param contactIds     ids of those contacts
 * @param errors         rows that were rejected, with the reason
 * @param unknownColumns header columns the importer ignored
 */
public record ContactImportResult(
        int added,
        List<Long> contactIds,
        List<CsvRowError> errors,
        List<String> unknownColumns
) {
}
