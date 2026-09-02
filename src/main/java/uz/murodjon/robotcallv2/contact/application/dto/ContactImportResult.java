package uz.murodjon.robotcallv2.contact.application.dto;

import uz.murodjon.robotcallv2.shared.csv.CsvRowError;

import java.util.List;

public record ContactImportResult(
        int added,
        List<Long> contactIds,
        List<CsvRowError> errors,
        List<String> unknownColumns
) {
}
