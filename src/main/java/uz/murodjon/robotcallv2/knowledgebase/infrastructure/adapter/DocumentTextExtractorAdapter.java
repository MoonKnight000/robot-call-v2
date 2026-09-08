package uz.murodjon.robotcallv2.knowledgebase.infrastructure.adapter;

import com.lowagie.text.pdf.PdfReader;
import com.lowagie.text.pdf.parser.PdfTextExtractor;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xwpf.extractor.XWPFWordExtractor;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import uz.murodjon.robotcallv2.knowledgebase.application.port.output.DocumentTextExtractorPort;
import uz.murodjon.robotcallv2.knowledgebase.domain.enums.KnowledgeSourceType;
import uz.murodjon.robotcallv2.shared.exception.ErrorCode;
import uz.murodjon.robotcallv2.shared.exception.ExternalServiceException;
import uz.murodjon.robotcallv2.shared.exception.ValidationException;
import uz.murodjon.robotcallv2.shared.util.PublicUrlGuard;

import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;

/**
 * Reads a knowledge source's bytes as text, using the libraries this build already
 * carries: OpenPDF for PDF, POI for the Office formats, and plain UTF-8 for the rest.
 *
 * <p>A scanned PDF has no text layer and comes back empty rather than failing — the
 * caller turns that into a {@code FAILED} source with a reason an operator can act on,
 * which is more useful than an exception about page objects.
 */
@Component
public class DocumentTextExtractorAdapter implements DocumentTextExtractorPort {

    private static final Logger log = LoggerFactory.getLogger(DocumentTextExtractorAdapter.class);

    /** A knowledge base is prose, not a data dump; past this the document is not being read, it is being scraped. */
    private static final int MAX_CHARS = 2_000_000;
    private static final int MAX_URL_BYTES = 8 * 1024 * 1024;

    // Redirects are never followed: PublicUrlGuard screens the address the company gave,
    // and a 302 is the site choosing a second one after that check has passed — which is
    // exactly how such a filter is walked around.
    private final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .followRedirects(HttpClient.Redirect.NEVER)
            .build();

    @Override
    public String extract(InputStream content, KnowledgeSourceType sourceType) {
        try (InputStream in = content) {
            return switch (sourceType) {
                case PDF -> extractPdf(in);
                case DOCX -> extractDocx(in);
                case XLSX, XLS -> extractSpreadsheet(in);
                case TXT, CSV -> truncate(new String(in.readAllBytes(), StandardCharsets.UTF_8));
                case URL -> throw new ValidationException(ErrorCode.KNOWLEDGE_SOURCE_URL_REQUIRED);
            };
        } catch (ValidationException e) {
            throw e;
        } catch (Exception e) {
            throw new ExternalServiceException(ErrorCode.KNOWLEDGE_SOURCE_UNREADABLE, "document-extractor", e,
                    sourceType, String.valueOf(e.getMessage()));
        }
    }

    @Override
    public String extractFromUrl(String url) {
        if (url == null || url.isBlank()) {
            throw new ValidationException(ErrorCode.KNOWLEDGE_SOURCE_URL_REQUIRED);
        }
        String trimmed = url.trim();
        URI uri = PublicUrlGuard.parsePublic(trimmed);
        if (uri == null) {
            throw new ValidationException(ErrorCode.KNOWLEDGE_SOURCE_URL_INVALID, trimmed);
        }
        try {
            HttpRequest request = HttpRequest.newBuilder(uri)
                    .timeout(Duration.ofSeconds(20))
                    // Some sites serve a JavaScript shell to an unknown agent and the real
                    // article to a browser; asking as a browser is what gets the text.
                    .header("User-Agent", "Mozilla/5.0 (compatible; RobotCallKnowledgeBase/1.0)")
                    .header("Accept", "text/html,application/xhtml+xml,text/plain")
                    .GET()
                    .build();
            HttpResponse<byte[]> response = httpClient.send(request, HttpResponse.BodyHandlers.ofByteArray());
            if (response.statusCode() / 100 != 2) {
                throw new ExternalServiceException(ErrorCode.KNOWLEDGE_SOURCE_FETCH_FAILED, "knowledge-url",
                        trimmed, response.statusCode());
            }
            byte[] body = response.body();
            if (body.length > MAX_URL_BYTES) {
                body = java.util.Arrays.copyOf(body, MAX_URL_BYTES);
            }
            String html = new String(body, StandardCharsets.UTF_8);
            return truncate(stripMarkup(html));
        } catch (ExternalServiceException | ValidationException e) {
            throw e;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new ExternalServiceException(ErrorCode.KNOWLEDGE_SOURCE_FETCH_FAILED, "knowledge-url", e,
                    trimmed, "interrupted");
        } catch (Exception e) {
            throw new ExternalServiceException(ErrorCode.KNOWLEDGE_SOURCE_FETCH_FAILED, "knowledge-url", e,
                    trimmed, String.valueOf(e.getMessage()));
        }
    }

    private static String extractPdf(InputStream in) throws Exception {
        PdfReader reader = new PdfReader(in);
        try {
            PdfTextExtractor extractor = new PdfTextExtractor(reader);
            StringBuilder text = new StringBuilder();
            for (int page = 1; page <= reader.getNumberOfPages() && text.length() < MAX_CHARS; page++) {
                try {
                    text.append(extractor.getTextFromPage(page)).append("\n\n");
                } catch (Exception e) {
                    // One malformed page must not cost the other two hundred.
                    log.debug("PDF page {} could not be read: {}", page, e.getMessage());
                }
            }
            return truncate(text.toString());
        } finally {
            reader.close();
        }
    }

    private static String extractDocx(InputStream in) throws Exception {
        try (XWPFDocument document = new XWPFDocument(in);
             XWPFWordExtractor extractor = new XWPFWordExtractor(document)) {
            return truncate(extractor.getText());
        }
    }

    /**
     * A spreadsheet read row by row, cells joined with " | ". Formatted values, not raw
     * ones: a due date stored as a serial number is 45678 to the model otherwise.
     */
    private static String extractSpreadsheet(InputStream in) throws Exception {
        try (Workbook workbook = WorkbookFactory.create(in)) {
            DataFormatter formatter = new DataFormatter();
            StringBuilder text = new StringBuilder();
            for (Sheet sheet : workbook) {
                text.append(sheet.getSheetName()).append('\n');
                for (Row row : sheet) {
                    StringBuilder line = new StringBuilder();
                    for (Cell cell : row) {
                        String value = formatter.formatCellValue(cell).trim();
                        if (!value.isEmpty()) {
                            if (!line.isEmpty()) {
                                line.append(" | ");
                            }
                            line.append(value);
                        }
                    }
                    if (!line.isEmpty()) {
                        text.append(line).append('\n');
                    }
                    if (text.length() >= MAX_CHARS) {
                        return truncate(text.toString());
                    }
                }
                text.append('\n');
            }
            return truncate(text.toString());
        }
    }

    /**
     * Enough of an HTML stripper for a documentation page: script and style bodies go
     * first (their contents are not prose), then tags, then entities. Deliberately not a
     * parser — the goal is readable text to embed, not a DOM.
     */
    private static String stripMarkup(String html) {
        String text = html
                .replaceAll("(?is)<(script|style|noscript|svg)[^>]*>.*?</\\1>", " ")
                .replaceAll("(?is)<!--.*?-->", " ")
                .replaceAll("(?i)<(br|/p|/div|/li|/tr|/h[1-6])[^>]*>", "\n")
                .replaceAll("(?s)<[^>]+>", " ");
        return text
                .replace("&nbsp;", " ")
                .replace("&amp;", "&")
                .replace("&lt;", "<")
                .replace("&gt;", ">")
                .replace("&quot;", "\"")
                .replace("&#39;", "'")
                .replaceAll("&[a-zA-Z#0-9]{2,8};", " ");
    }

    private static String truncate(String text) {
        if (text == null) {
            return "";
        }
        return text.length() > MAX_CHARS ? text.substring(0, MAX_CHARS) : text;
    }
}
