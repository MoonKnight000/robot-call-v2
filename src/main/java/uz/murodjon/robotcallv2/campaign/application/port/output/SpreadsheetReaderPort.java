package uz.murodjon.robotcallv2.campaign.application.port.output;

import uz.murodjon.robotcallv2.campaign.domain.entity.SpreadsheetCsv;

/** Reads an uploaded .xlsx workbook as CSV. */
public interface SpreadsheetReaderPort {

    /**
     * @param sheet   the worksheet's index; one past the last falls back to the first
     * @param maxRows data rows read at most — one more than that is read, so the caller can tell the sheet is longer
     * @return the worksheet, or null when the bytes are not a readable workbook
     */
    SpreadsheetCsv readSheet(byte[] xlsx, int sheet, int maxRows);
}
