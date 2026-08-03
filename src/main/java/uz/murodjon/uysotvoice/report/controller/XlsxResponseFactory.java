package uz.murodjon.uysotvoice.report.controller;

import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.Font;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;

import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;

import uz.murodjon.uysotvoice.report.dto.CampaignComparisonRow;
import uz.murodjon.uysotvoice.report.dto.DashboardOutcome;
import uz.murodjon.uysotvoice.report.dto.FunnelStage;
import uz.murodjon.uysotvoice.report.dto.ReportSummary;
import uz.murodjon.uysotvoice.shared.exception.ExternalServiceException;

import java.io.ByteArrayOutputStream;

/**
 * The {@code xlsx} branch of {@code GET /api/reports/export} and the scheduled email
 * attachment (§10.10) — one sheet per {@link ReportSummary} section, built with Apache
 * POI.
 */
@Component
public class XlsxResponseFactory {

    private static final MediaType XLSX =
            MediaType.parseMediaType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");

    public ResponseEntity<byte[]> toResponse(String filename, ReportSummary summary) {
        return ResponseEntity.ok()
                .contentType(XLSX)
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + filename + "\"")
                .body(render(summary));
    }

    private static byte[] render(ReportSummary summary) {
        try (XSSFWorkbook workbook = new XSSFWorkbook();
             ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            CellStyle headerStyle = headerStyle(workbook);

            Sheet totals = workbook.createSheet("Umumiy");
            String[][] totalsRows = {
                    {"from", summary.from().toString()},
                    {"to", summary.to().toString()},
                    {"total_calls", String.valueOf(summary.totals().totalCalls())},
                    {"answered_calls", String.valueOf(summary.totals().answeredCalls())},
                    {"avg_duration_sec", summary.totals().avgDurationSec() != null
                            ? summary.totals().avgDurationSec().toString() : ""},
                    {"promises", String.valueOf(summary.totals().promises())},
            };
            writeSheet(totals, headerStyle, new String[]{"metric", "value"}, totalsRows.length,
                    (row, i) -> {
                        row.createCell(0).setCellValue(totalsRows[i][0]);
                        row.createCell(1).setCellValue(totalsRows[i][1]);
                    });

            Sheet outcomes = workbook.createSheet("Natijalar");
            writeSheet(outcomes, headerStyle, new String[]{"disposition", "count"}, summary.outcomes().size(),
                    (row, i) -> {
                        DashboardOutcome o = summary.outcomes().get(i);
                        row.createCell(0).setCellValue(o.disposition());
                        row.createCell(1).setCellValue(o.count());
                    });

            Sheet funnel = workbook.createSheet("Voronka");
            writeSheet(funnel, headerStyle, new String[]{"stage", "count", "rate"}, summary.funnel().size(),
                    (row, i) -> {
                        FunnelStage f = summary.funnel().get(i);
                        row.createCell(0).setCellValue(f.stage());
                        row.createCell(1).setCellValue(f.count());
                        row.createCell(2).setCellValue(f.rate());
                    });

            if (!summary.campaigns().isEmpty()) {
                Sheet campaigns = workbook.createSheet("Kampaniyalar");
                writeSheet(campaigns, headerStyle,
                        new String[]{"campaign_id", "campaign_name", "total_calls", "answered_calls",
                                "answer_rate", "avg_duration_sec", "promises"},
                        summary.campaigns().size(),
                        (row, i) -> {
                            CampaignComparisonRow c = summary.campaigns().get(i);
                            row.createCell(0).setCellValue(c.campaignId());
                            row.createCell(1).setCellValue(c.campaignName());
                            row.createCell(2).setCellValue(c.totalCalls());
                            row.createCell(3).setCellValue(c.answeredCalls());
                            row.createCell(4).setCellValue(c.answerRate());
                            row.createCell(5).setCellValue(c.avgDurationSec() != null ? c.avgDurationSec() : 0);
                            row.createCell(6).setCellValue(c.promises());
                        });
            }

            workbook.write(out);
            return out.toByteArray();
        } catch (Exception e) {
            throw new ExternalServiceException("xlsx", "Failed to render report XLSX: " + e.getMessage(), e);
        }
    }

    @FunctionalInterface
    private interface RowWriter {
        void write(Row row, int index);
    }

    private static void writeSheet(Sheet sheet, CellStyle headerStyle, String[] headers, int rowCount,
                                   RowWriter writer) {
        Row headerRow = sheet.createRow(0);
        for (int c = 0; c < headers.length; c++) {
            Cell cell = headerRow.createCell(c);
            cell.setCellValue(headers[c]);
            cell.setCellStyle(headerStyle);
        }
        for (int i = 0; i < rowCount; i++) {
            writer.write(sheet.createRow(i + 1), i);
        }
        for (int c = 0; c < headers.length; c++) {
            sheet.autoSizeColumn(c);
        }
    }

    private static CellStyle headerStyle(XSSFWorkbook workbook) {
        Font bold = workbook.createFont();
        bold.setBold(true);
        CellStyle style = workbook.createCellStyle();
        style.setFont(bold);
        return style;
    }
}
