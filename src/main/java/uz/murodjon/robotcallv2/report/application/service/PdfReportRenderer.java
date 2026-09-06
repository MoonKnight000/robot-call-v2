package uz.murodjon.robotcallv2.report.application.service;

import com.lowagie.text.*;
import com.lowagie.text.pdf.PdfPCell;
import com.lowagie.text.pdf.PdfPTable;
import com.lowagie.text.pdf.PdfWriter;
import org.springframework.stereotype.Component;
import uz.murodjon.robotcallv2.report.domain.entity.CampaignComparisonRow;
import uz.murodjon.robotcallv2.report.domain.entity.DashboardOutcome;
import uz.murodjon.robotcallv2.report.domain.entity.FunnelStage;
import uz.murodjon.robotcallv2.report.domain.entity.ReportSummary;
import uz.murodjon.robotcallv2.shared.exception.ErrorCode;
import uz.murodjon.robotcallv2.shared.exception.ExternalServiceException;

import java.io.ByteArrayOutputStream;

/**
 * The {@code pdf} branch of {@code GET /api/reports/export} and the scheduled email
 * attachment (§10.10) — a printable one-page-per-section digest of {@link
 * ReportSummary}, built with OpenPDF since the project had no PDF writer before.
 */
@Component
public class PdfReportRenderer {

    private static final String PDF = "application/pdf";
    private static final Font TITLE = new Font(Font.HELVETICA, 18, Font.BOLD);
    private static final Font HEADING = new Font(Font.HELVETICA, 13, Font.BOLD);
    private static final Font BODY = new Font(Font.HELVETICA, 10, Font.NORMAL);
    private static final Font TABLE_HEADER = new Font(Font.HELVETICA, 10, Font.BOLD);

    /** The {@code GET /api/reports/export?format=pdf} content type. */
    public String contentType() {
        return PDF;
    }

    public byte[] renderBytes(ReportSummary summary) {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        Document document = new Document(PageSize.A4, 36, 36, 54, 36);
        try {
            PdfWriter.getInstance(document, out);
            document.open();

            document.add(new Paragraph("Uysot Voice — hisobot", TITLE));
            document.add(new Paragraph(summary.from() + " — " + summary.to()
                    + (summary.campaignId() != null ? " (campaign #" + summary.campaignId() + ")" : ""), BODY));
            document.add(spacer());

            document.add(new Paragraph("Umumiy ko'rsatkichlar", HEADING));
            document.add(new Paragraph("Jami qo'ng'iroqlar: " + summary.totals().totalCalls(), BODY));
            document.add(new Paragraph("Javob berilgan: " + summary.totals().answeredCalls(), BODY));
            document.add(new Paragraph("O'rtacha davomiylik (sek): "
                    + (summary.totals().avgDurationSec() != null
                            ? String.format("%.1f", summary.totals().avgDurationSec()) : "—"), BODY));
            document.add(new Paragraph("Va'dalar soni: " + summary.totals().promises(), BODY));
            document.add(spacer());

            document.add(new Paragraph("Natijalar taqsimoti", HEADING));
            PdfPTable outcomes = headerRow("Natija", "Soni");
            for (DashboardOutcome o : summary.outcomes()) {
                outcomes.addCell(cell(o.disposition()));
                outcomes.addCell(cell(String.valueOf(o.count())));
            }
            document.add(outcomes);
            document.add(spacer());

            document.add(new Paragraph("Voronka", HEADING));
            PdfPTable funnel = headerRow("Bosqich", "Soni", "Ulush");
            for (FunnelStage f : summary.funnel()) {
                funnel.addCell(cell(f.stage()));
                funnel.addCell(cell(String.valueOf(f.count())));
                funnel.addCell(cell(String.format("%.1f%%", f.rate() * 100)));
            }
            document.add(funnel);

            if (!summary.campaigns().isEmpty()) {
                document.add(spacer());
                document.add(new Paragraph("Kampaniyalar taqqoslash", HEADING));
                PdfPTable campaigns = headerRow("Kampaniya", "Jami", "Javob %", "O'rtacha (sek)", "Va'dalar");
                for (CampaignComparisonRow c : summary.campaigns()) {
                    campaigns.addCell(cell(c.campaignName()));
                    campaigns.addCell(cell(String.valueOf(c.totalCalls())));
                    campaigns.addCell(cell(String.format("%.1f%%", c.answerRate() * 100)));
                    campaigns.addCell(cell(c.avgDurationSec() != null
                            ? String.format("%.1f", c.avgDurationSec()) : "—"));
                    campaigns.addCell(cell(String.valueOf(c.promises())));
                }
                document.add(campaigns);
            }
        } catch (Exception e) {
            throw new ExternalServiceException(ErrorCode.REPORT_PDF_RENDER_FAILED, "pdf", e, e.getMessage());
        } finally {
            document.close();
        }
        return out.toByteArray();
    }

    private static PdfPTable headerRow(String... headers) {
        PdfPTable table = new PdfPTable(headers.length);
        table.setWidthPercentage(100);
        for (String h : headers) {
            PdfPCell cell = new PdfPCell(new Paragraph(h, TABLE_HEADER));
            cell.setHorizontalAlignment(Element.ALIGN_LEFT);
            table.addCell(cell);
        }
        return table;
    }

    private static PdfPCell cell(String text) {
        return new PdfPCell(new Paragraph(text, BODY));
    }

    /** A blank line between sections — OpenPDF has no bare vertical-space primitive. */
    private static Paragraph spacer() {
        Paragraph p = new Paragraph(" ", BODY);
        p.setSpacingAfter(4f);
        return p;
    }
}

