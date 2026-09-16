package uz.murodjon.robotcallv2.knowledgebase.infrastructure.adapter;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.DataFormatter;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.ss.usermodel.WorkbookFactory;
import org.springframework.stereotype.Component;
import uz.murodjon.robotcallv2.knowledgebase.application.port.output.CatalogFeedReaderPort;
import uz.murodjon.robotcallv2.knowledgebase.domain.enums.KnowledgeSourceType;
import uz.murodjon.robotcallv2.shared.exception.ErrorCode;
import uz.murodjon.robotcallv2.shared.exception.ExternalServiceException;
import uz.murodjon.robotcallv2.shared.http.PublicHttpClient;
import uz.murodjon.robotcallv2.shared.http.PublicHttpRequest;
import uz.murodjon.robotcallv2.shared.http.PublicHttpResponse;

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Reads a catalog feed as rows (UNIVERSAL-OPERATOR-PLAN B.4): a CSV, a spreadsheet or a
 * JSON array, uploaded or fetched from the shop's own list endpoint.
 *
 * <p>Every row is a map of the columns the feed names, as text — which column is the key
 * is the source's business ({@code CatalogItems}), not the reader's. A URL is fetched
 * through the shared pinned client, the same way a knowledge URL is: the address a tenant
 * gave is screened before this is called, and no redirect is followed after.
 */
@Component
public class CatalogFeedReaderAdapter implements CatalogFeedReaderPort {

    /** A catalog this large is read; past it the rest of the feed is not. */
    private static final int MAX_ROWS = 50_000;
    private static final int MAX_FEED_BYTES = 32 * 1024 * 1024;
    private static final Duration FEED_TIMEOUT = Duration.ofSeconds(30);

    private static final ObjectMapper JSON = new ObjectMapper();

    private final PublicHttpClient httpClient = PublicHttpClient.create(Duration.ofSeconds(10), FEED_TIMEOUT);

    @Override
    public List<Map<String, String>> parseRows(InputStream content, String fileName) {
        String name = fileName == null ? "" : fileName.toLowerCase(Locale.ROOT);
        try (InputStream in = content) {
            if (name.endsWith(".xlsx") || name.endsWith(".xls")) {
                return spreadsheetRows(in);
            }
            String text = new String(in.readAllBytes(), StandardCharsets.UTF_8);
            return name.endsWith(".json") ? jsonRows(text, null) : csvRows(text);
        } catch (ExternalServiceException e) {
            throw e;
        } catch (Exception e) {
            throw new ExternalServiceException(ErrorCode.KNOWLEDGE_SOURCE_UNREADABLE, "catalog-feed", e,
                    KnowledgeSourceType.STRUCTURED_FEED, String.valueOf(e.getMessage()));
        }
    }

    @Override
    public List<Map<String, String>> fetchRows(URI uri, String itemsPath) {
        String address = uri.toString();
        try {
            PublicHttpRequest request = PublicHttpRequest.create("GET", uri)
                    .setTimeout(FEED_TIMEOUT)
                    .setMaxResponseBytes(MAX_FEED_BYTES)
                    .addHeader("Accept", "application/json,text/csv,text/plain");
            PublicHttpResponse response = httpClient.send(request);
            if (response.statusCode() / 100 != 2) {
                throw new ExternalServiceException(ErrorCode.KNOWLEDGE_SOURCE_FETCH_FAILED, "catalog-feed",
                        address, response.statusCode());
            }
            String body = response.readBodyText();
            String start = body.stripLeading();
            return start.startsWith("[") || start.startsWith("{") ? jsonRows(body, itemsPath) : csvRows(body);
        } catch (ExternalServiceException e) {
            throw e;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new ExternalServiceException(ErrorCode.KNOWLEDGE_SOURCE_FETCH_INTERRUPTED, "catalog-feed", e, address);
        } catch (Exception e) {
            throw new ExternalServiceException(ErrorCode.KNOWLEDGE_SOURCE_FETCH_FAILED, "catalog-feed", e,
                    address, String.valueOf(e.getMessage()));
        }
    }

    /** The objects of a JSON array — the whole answer, or the one at {@code itemsPath}; scalars as text, nested values as JSON. */
    private static List<Map<String, String>> jsonRows(String text, String itemsPath) throws IOException {
        JsonNode root = JSON.readTree(text);
        boolean wholeAnswer = itemsPath == null || itemsPath.isBlank();
        JsonNode items = wholeAnswer ? root : root.at(toPointer(itemsPath));
        if (!items.isArray()) {
            throw new ExternalServiceException(ErrorCode.KNOWLEDGE_SOURCE_UNREADABLE, "catalog-feed",
                    KnowledgeSourceType.STRUCTURED_FEED, "no array of items at " + (wholeAnswer ? "$" : itemsPath));
        }
        List<Map<String, String>> rows = new ArrayList<>();
        for (JsonNode item : items) {
            if (rows.size() >= MAX_ROWS) {
                break;
            }
            if (!item.isObject()) {
                continue;
            }
            Map<String, String> row = new LinkedHashMap<>();
            item.fields().forEachRemaining(field -> {
                JsonNode value = field.getValue();
                if (!value.isNull()) {
                    row.put(field.getKey(), value.isValueNode() ? value.asText() : value.toString());
                }
            });
            rows.add(row);
        }
        return rows;
    }

    /** {@code $.data.items} as the JSON Pointer Jackson reads, {@code /data/items}; {@code [0]} becomes {@code /0}. */
    private static String toPointer(String path) {
        String trimmed = path.trim();
        String body = trimmed.startsWith("$") ? trimmed.substring(1) : trimmed;
        StringBuilder pointer = new StringBuilder();
        for (String segment : body.split("[.\\[\\]]")) {
            if (!segment.isEmpty()) {
                pointer.append('/').append(segment.replace("~", "~0").replace("/", "~1"));
            }
        }
        return pointer.toString();
    }

    /** The first sheet; its first non-empty row names the columns. Formatted values, as a person reads them. */
    private static List<Map<String, String>> spreadsheetRows(InputStream in) throws Exception {
        try (Workbook workbook = WorkbookFactory.create(in)) {
            DataFormatter formatter = new DataFormatter();
            Sheet sheet = workbook.getSheetAt(0);
            List<String> header = null;
            List<Map<String, String>> rows = new ArrayList<>();
            for (Row row : sheet) {
                List<String> values = new ArrayList<>();
                for (int c = 0; c < Math.max(row.getLastCellNum(), 0); c++) {
                    Cell cell = row.getCell(c);
                    values.add(cell == null ? "" : formatter.formatCellValue(cell).trim());
                }
                if (values.stream().allMatch(String::isEmpty)) {
                    continue;
                }
                if (header == null) {
                    header = values;
                    continue;
                }
                rows.add(toRow(header, values));
                if (rows.size() >= MAX_ROWS) {
                    break;
                }
            }
            return rows;
        }
    }

    private static List<Map<String, String>> csvRows(String text) {
        String body = text.startsWith("﻿") ? text.substring(1) : text;
        List<List<String>> records = parseCsv(body, delimiterOf(body));
        if (records.isEmpty()) {
            return List.of();
        }
        List<String> header = records.get(0);
        List<Map<String, String>> rows = new ArrayList<>();
        for (int r = 1; r < records.size() && rows.size() < MAX_ROWS; r++) {
            rows.add(toRow(header, records.get(r)));
        }
        return rows;
    }

    /** What the header line separates with most: a comma, a semicolon (Excel in a comma-decimal locale) or a tab. */
    private static char delimiterOf(String text) {
        int end = text.indexOf('\n');
        String header = end < 0 ? text : text.substring(0, end);
        char best = ',';
        long bestCount = -1;
        for (char candidate : new char[]{',', ';', '\t'}) {
            long count = header.chars().filter(c -> c == candidate).count();
            if (count > bestCount) {
                best = candidate;
                bestCount = count;
            }
        }
        return best;
    }

    /** RFC 4180: a quoted field may hold the separator, a line break and doubled quotes. Blank lines are skipped. */
    private static List<List<String>> parseCsv(String text, char delimiter) {
        List<List<String>> records = new ArrayList<>();
        List<String> record = new ArrayList<>();
        StringBuilder field = new StringBuilder();
        boolean quoted = false;
        for (int i = 0; i < text.length() && records.size() <= MAX_ROWS; i++) {
            char c = text.charAt(i);
            if (quoted) {
                if (c == '"' && i + 1 < text.length() && text.charAt(i + 1) == '"') {
                    field.append('"');
                    i++;
                } else if (c == '"') {
                    quoted = false;
                } else {
                    field.append(c);
                }
            } else if (c == '"') {
                quoted = true;
            } else if (c == delimiter) {
                record.add(field.toString());
                field.setLength(0);
            } else if (c == '\n' || c == '\r') {
                if (c == '\r' && i + 1 < text.length() && text.charAt(i + 1) == '\n') {
                    i++;
                }
                record.add(field.toString());
                field.setLength(0);
                addRecord(records, record);
                record = new ArrayList<>();
            } else {
                field.append(c);
            }
        }
        if (!field.isEmpty() || !record.isEmpty()) {
            record.add(field.toString());
            addRecord(records, record);
        }
        return records;
    }

    private static void addRecord(List<List<String>> records, List<String> record) {
        if (!(record.size() == 1 && record.get(0).isBlank())) {
            records.add(record);
        }
    }

    private static Map<String, String> toRow(List<String> header, List<String> values) {
        Map<String, String> row = new LinkedHashMap<>();
        for (int i = 0; i < header.size() && i < values.size(); i++) {
            String column = header.get(i).trim();
            if (!column.isEmpty()) {
                row.put(column, values.get(i).trim());
            }
        }
        return row;
    }
}
