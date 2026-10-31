package uz.murodjon.robotcallv2.campaign.infrastructure.adapter;

import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.DataFormatter;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.ss.usermodel.WorkbookFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import uz.murodjon.robotcallv2.campaign.application.port.output.SpreadsheetReaderPort;
import uz.murodjon.robotcallv2.campaign.domain.entity.SpreadsheetCsv;

import java.io.ByteArrayInputStream;
import java.util.ArrayList;
import java.util.List;

/**
 * Turns one worksheet of an .xlsx file into CSV with Apache POI. Cells are written as a person sees them in Excel
 * (a date as a date, not its serial number); the first non-empty row is the header, empty rows are skipped.
 */
@Component
public class PoiSpreadsheetReaderAdapter implements SpreadsheetReaderPort {

    private static final Logger log = LoggerFactory.getLogger(PoiSpreadsheetReaderAdapter.class);

    @Override
    public SpreadsheetCsv readSheet(byte[] xlsx, int sheet, int maxRows) {
        try (Workbook workbook = WorkbookFactory.create(new ByteArrayInputStream(xlsx))) {
            List<String> names = new ArrayList<>();
            for (int i = 0; i < workbook.getNumberOfSheets(); i++) {
                names.add(workbook.getSheetName(i));
            }
            int index = sheet >= 0 && sheet < names.size() ? sheet : 0;
            DataFormatter formatter = new DataFormatter();
            StringBuilder csv = new StringBuilder();
            int lines = 0;
            for (Row row : workbook.getSheetAt(index)) {
                List<String> values = new ArrayList<>();
                for (int c = 0; c < Math.max(row.getLastCellNum(), 0); c++) {
                    Cell cell = row.getCell(c);
                    values.add(cell == null ? "" : formatter.formatCellValue(cell).trim());
                }
                if (values.stream().allMatch(String::isEmpty)) {
                    continue;
                }
                csv.append(String.join(",", values.stream().map(PoiSpreadsheetReaderAdapter::csvCell).toList())).append('\n');
                lines++;
                if (lines > maxRows + 1) {
                    break; // header + maxRows + one more: enough to tell the sheet is too long
                }
            }
            return new SpreadsheetCsv(names, index, csv.toString(), Math.max(0, lines - 1));
        } catch (Exception e) {
            log.info("Uploaded spreadsheet could not be read: {}", e.getMessage());
            return null;
        }
    }

    /** One value as a CSV cell; a line break inside a cell becomes a space, since the importer reads line by line. */
    private static String csvCell(String value) {
        String flat = value.replace("\r\n", " ").replace('\n', ' ').replace('\r', ' ');
        return flat.contains(",") || flat.contains("\"") ? "\"" + flat.replace("\"", "\"\"") + "\"" : flat;
    }
}
