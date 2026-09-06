package uz.murodjon.robotcallv2.billing.application.service;

import com.lowagie.text.*;
import com.lowagie.text.pdf.PdfPCell;
import com.lowagie.text.pdf.PdfPTable;
import com.lowagie.text.pdf.PdfWriter;
import org.springframework.stereotype.Service;
import uz.murodjon.robotcallv2.billing.domain.entity.Invoice;
import uz.murodjon.robotcallv2.shared.exception.ErrorCode;
import uz.murodjon.robotcallv2.shared.exception.ExternalServiceException;

import java.awt.Color;
import java.io.ByteArrayOutputStream;
import java.text.NumberFormat;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Locale;

@Service
public class InvoicePdfService {

    private static final Font TITLE_FONT = new Font(Font.HELVETICA, 18, Font.BOLD, new Color(33, 37, 41));
    private static final Font SUBTITLE_FONT = new Font(Font.HELVETICA, 11, Font.NORMAL, new Color(108, 117, 125));
    private static final Font HEADING_FONT = new Font(Font.HELVETICA, 12, Font.BOLD, new Color(33, 37, 41));
    private static final Font BODY_FONT = new Font(Font.HELVETICA, 10, Font.NORMAL, new Color(52, 58, 64));
    private static final Font BOLD_FONT = new Font(Font.HELVETICA, 10, Font.BOLD, new Color(33, 37, 41));
    private static final Font FOOTER_FONT = new Font(Font.HELVETICA, 8, Font.ITALIC, new Color(134, 142, 150));

    private static final DateTimeFormatter DATE_FMT = DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm")
            .withZone(ZoneId.of("Asia/Tashkent"));

    public byte[] generateInvoicePdf(Invoice invoice, String companyName) {
        Document document = new Document(PageSize.A4, 40, 40, 45, 45);
        try (ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            PdfWriter.getInstance(document, out);
            document.open();

            // Header table: Logo / Company info and Invoice badge
            PdfPTable header = new PdfPTable(2);
            header.setWidthPercentage(100);
            header.setWidths(new float[]{60, 40});

            PdfPCell companyCell = new PdfPCell();
            companyCell.setBorder(Rectangle.NO_BORDER);
            companyCell.addElement(new Paragraph("ROBOT CALL AI PLATFORM", TITLE_FONT));
            companyCell.addElement(new Paragraph("Avtomatlashtirilgan Ovozli Aloqa Tizimi", SUBTITLE_FONT));
            companyCell.addElement(new Paragraph("Toshkent sh., O'zbekiston", SUBTITLE_FONT));
            header.addCell(companyCell);

            PdfPCell invoiceMetaCell = new PdfPCell();
            invoiceMetaCell.setBorder(Rectangle.NO_BORDER);
            invoiceMetaCell.setHorizontalAlignment(Element.ALIGN_RIGHT);
            Paragraph invTitle = new Paragraph("HISOB-FAKTURA", HEADING_FONT);
            invTitle.setAlignment(Element.ALIGN_RIGHT);
            invoiceMetaCell.addElement(invTitle);

            Paragraph invNumber = new Paragraph("№ " + invoice.id(), BOLD_FONT);
            invNumber.setAlignment(Element.ALIGN_RIGHT);
            invoiceMetaCell.addElement(invNumber);

            Paragraph invDate = new Paragraph("Sana: " + (invoice.createdAt() != null ? DATE_FMT.format(invoice.createdAt()) : "-"), BODY_FONT);
            invDate.setAlignment(Element.ALIGN_RIGHT);
            invoiceMetaCell.addElement(invDate);

            Paragraph invStatus = new Paragraph("Holati: " + invoice.status().name(), BOLD_FONT);
            invStatus.setAlignment(Element.ALIGN_RIGHT);
            invoiceMetaCell.addElement(invStatus);

            header.addCell(invoiceMetaCell);
            document.add(header);

            document.add(spacer(15));

            // Customer info
            PdfPTable billTo = new PdfPTable(1);
            billTo.setWidthPercentage(100);
            PdfPCell billToCell = new PdfPCell();
            billToCell.setBackgroundColor(new Color(245, 247, 250));
            billToCell.setPadding(10);
            billToCell.setBorderColor(new Color(222, 226, 230));
            billToCell.addElement(new Paragraph("Mijoz (Buyurtmachi):", BOLD_FONT));
            billToCell.addElement(new Paragraph("Tashkilot: " + (companyName != null ? companyName : "Kompaniya ID #" + invoice.companyId()), BODY_FONT));
            billToCell.addElement(new Paragraph("Hisob davri: " + invoice.periodName(), BODY_FONT));
            if (invoice.paidAt() != null) {
                billToCell.addElement(new Paragraph("To'langan sana: " + DATE_FMT.format(invoice.paidAt()), BODY_FONT));
            }
            billTo.addCell(billToCell);
            document.add(billTo);

            document.add(spacer(20));

            // Items table
            PdfPTable items = new PdfPTable(4);
            items.setWidthPercentage(100);
            items.setWidths(new float[]{10, 50, 20, 20});

            addTableHeader(items, "№", "Xizmat tavsifi", "Miqdori", "Summa (so'm)");

            NumberFormat numFmt = NumberFormat.getInstance(Locale.US);
            String formattedAmount = numFmt.format(invoice.amountUzs()).replace(',', ' ');

            addTableRow(items, "1", "Robot Call Platform xizmatlari (" + invoice.periodName() + ")", "1 oylik", formattedAmount);
            addTableRow(items, "2", "AI ovozli modellar va STT/TTS trafigi", "Paket ichida", "0");
            addTableRow(items, "3", "SIP Trunking va qo'ng'iroqlar liniyalari", "30 kanal", "0");

            document.add(items);

            document.add(spacer(15));

            // Summary table (Total)
            PdfPTable summary = new PdfPTable(2);
            summary.setWidthPercentage(40);
            summary.setHorizontalAlignment(Element.ALIGN_RIGHT);
            summary.setWidths(new float[]{50, 50});

            PdfPCell labelTotal = new PdfPCell(new Phrase("Jami to'lov:", BOLD_FONT));
            labelTotal.setBorder(Rectangle.NO_BORDER);
            labelTotal.setPadding(6);
            summary.addCell(labelTotal);

            PdfPCell valTotal = new PdfPCell(new Phrase(formattedAmount + " UZS", TITLE_FONT));
            valTotal.setBorder(Rectangle.NO_BORDER);
            valTotal.setHorizontalAlignment(Element.ALIGN_RIGHT);
            valTotal.setPadding(6);
            summary.addCell(valTotal);

            document.add(summary);

            document.add(spacer(30));

            // Footer notes
            Paragraph note = new Paragraph("Ushbu hujjat elektron tarzda shakllantirilgan va muhrsiz haqiqiydir. "
                    + "Savollar bo'yicha qo'llab-quvvatlash xizmatiga murojaat qiling.", FOOTER_FONT);
            note.setAlignment(Element.ALIGN_CENTER);
            document.add(note);

            document.close();
            return out.toByteArray();
        } catch (Exception e) {
            throw new ExternalServiceException(ErrorCode.INVOICE_PDF_RENDER_FAILED, "pdf", e, e.getMessage());
        }
    }

    private static void addTableHeader(PdfPTable table, String... headers) {
        for (String header : headers) {
            PdfPCell cell = new PdfPCell(new Phrase(header, BOLD_FONT));
            cell.setBackgroundColor(new Color(233, 236, 239));
            cell.setPadding(6);
            cell.setBorderColor(new Color(206, 212, 218));
            table.addCell(cell);
        }
    }

    private static void addTableRow(PdfPTable table, String col1, String col2, String col3, String col4) {
        PdfPCell c1 = new PdfPCell(new Phrase(col1, BODY_FONT));
        PdfPCell c2 = new PdfPCell(new Phrase(col2, BODY_FONT));
        PdfPCell c3 = new PdfPCell(new Phrase(col3, BODY_FONT));
        PdfPCell c4 = new PdfPCell(new Phrase(col4, BODY_FONT));
        c1.setPadding(6);
        c2.setPadding(6);
        c3.setPadding(6);
        c4.setPadding(6);
        c1.setBorderColor(new Color(222, 226, 230));
        c2.setBorderColor(new Color(222, 226, 230));
        c3.setBorderColor(new Color(222, 226, 230));
        c4.setBorderColor(new Color(222, 226, 230));
        c4.setHorizontalAlignment(Element.ALIGN_RIGHT);
        table.addCell(c1);
        table.addCell(c2);
        table.addCell(c3);
        table.addCell(c4);
    }

    private static Paragraph spacer(int height) {
        Paragraph p = new Paragraph(" ");
        p.setSpacingBefore(height);
        return p;
    }
}
