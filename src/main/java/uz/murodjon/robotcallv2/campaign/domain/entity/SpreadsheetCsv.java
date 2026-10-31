package uz.murodjon.robotcallv2.campaign.domain.entity;

import java.util.List;

/**
 * One worksheet of an .xlsx file as CSV text, so the rest of the import reads it as it reads an uploaded CSV.
 *
 * @param sheetNames every worksheet in the workbook, for picking another one
 * @param sheet      the index of the worksheet in {@code csv}
 * @param csv        its rows as values a person sees them, header row first
 * @param rows       its data rows (the header not counted)
 */
public record SpreadsheetCsv(List<String> sheetNames, int sheet, String csv, int rows) {
}
