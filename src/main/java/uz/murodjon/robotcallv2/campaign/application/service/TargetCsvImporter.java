package uz.murodjon.robotcallv2.campaign.application.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import uz.murodjon.robotcallv2.campaign.application.dto.CsvColumnMapping;
import uz.murodjon.robotcallv2.campaign.application.dto.ParsedTarget;
import uz.murodjon.robotcallv2.campaign.application.dto.TargetCsvParseResult;
import uz.murodjon.robotcallv2.shared.csv.CsvRowError;
import uz.murodjon.robotcallv2.shared.exception.ErrorCode;
import uz.murodjon.robotcallv2.shared.exception.ValidationException;

import java.util.*;

/**
 * Parses a CSV of campaign targets with multi-lingual auto-detection and dynamic context fields (PROJECT.md §10).
 */
public final class TargetCsvImporter {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private static final String COL_CLIENT_ID = "clientId";
    private static final String COL_PHONE = "phone";
    private static final String COL_LANGUAGE = "language";

    // Standard synonymous mapping for common Uzbek, Russian, and English headers
    private static final Map<String, String> KNOWN_ALIASES = new HashMap<>();

    static {
        // Phone aliases
        List.of("phone", "tel", "telefon", "telefonraqam", "raqam", "phonenumber", "nomer", "nomertelefona", "mobile", "mobil")
                .forEach(k -> KNOWN_ALIASES.put(k, COL_PHONE));

        // Client ID aliases
        List.of("clientid", "id", "mijozid", "kod", "user_id", "userid", "customerid")
                .forEach(k -> KNOWN_ALIASES.put(k, COL_CLIENT_ID));

        // Language aliases
        List.of("language", "lang", "til", "yazyk", "yazik")
                .forEach(k -> KNOWN_ALIASES.put(k, COL_LANGUAGE));

        // Client Name aliases
        List.of("clientname", "name", "ism", "fio", "fullname", "mijoz", "mijozismi", "imya", "fio_klienta")
                .forEach(k -> KNOWN_ALIASES.put(k, "clientName"));

        // Debt Amount aliases
        List.of("debtamount", "debt", "amount", "summa", "qarz", "qarzsummasi", "qarzdorlik", "dolg", "summadolga", "totaldebt")
                .forEach(k -> KNOWN_ALIASES.put(k, "debtAmount"));

        // Debt Days / Overdue Days aliases
        List.of("debtday", "debtdays", "overduedays", "delaydays", "daysoverdue", "qaszkuni", "kechikishkunlari", "kechikish", "procrochka", "dneyprosrochki", "prosrochkadays")
                .forEach(k -> KNOWN_ALIASES.put(k, "debtDay"));

        // Due Date aliases
        List.of("duedate", "deadline", "muddat", "tolashmuddati", "oxirgimuddat", "srok", "srokoplaty", "paydate")
                .forEach(k -> KNOWN_ALIASES.put(k, "dueDate"));

        // Contract Number aliases
        List.of("contractnumber", "contract", "contractno", "shartnoma", "shartnomanomeri", "shartnomaraqami", "dogovor", "nomerdogovora")
                .forEach(k -> KNOWN_ALIASES.put(k, "contractNumber"));

        // Currency
        List.of("currency", "valyuta", "valyutanomi").forEach(k -> KNOWN_ALIASES.put(k, "currency"));

        // Goal
        List.of("goal", "maqsad", "cel").forEach(k -> KNOWN_ALIASES.put(k, "goal"));
    }

    private TargetCsvImporter() {
    }

    public static List<CsvColumnMapping> mapColumns(String csv) {
        List<String> lines = splitLines(csv);
        if (lines.isEmpty()) {
            throw new ValidationException(ErrorCode.CSV_EMPTY);
        }
        char delimiter = detectDelimiter(lines.get(0));
        List<String> header = splitRow(lines.get(0), delimiter);
        List<CsvColumnMapping> mapping = new ArrayList<>();
        for (String h : header) {
            String norm = normalize(h);
            String mapped = KNOWN_ALIASES.get(norm);
            mapping.add(new CsvColumnMapping(h.trim(), mapped));
        }
        return mapping;
    }

    public static TargetCsvParseResult parse(String csv) {
        List<String> lines = splitLines(csv);
        if (lines.isEmpty()) {
            throw new ValidationException(ErrorCode.CSV_EMPTY);
        }
        char delimiter = detectDelimiter(lines.get(0));
        List<String> header = splitRow(lines.get(0), delimiter);

        // Header mapping: key = mapped field name, value = column index in CSV
        Map<String, Integer> fieldToIndex = new LinkedHashMap<>();
        // For dynamic/unmapped columns: key = raw header name, value = column index in CSV
        Map<String, Integer> dynamicHeaders = new LinkedHashMap<>();
        List<String> dynamicHeaderNames = new ArrayList<>();

        for (int i = 0; i < header.size(); i++) {
            String rawHeader = header.get(i).trim();
            String norm = normalize(rawHeader);
            if (norm.isEmpty()) {
                continue;
            }
            if (KNOWN_ALIASES.containsKey(norm)) {
                fieldToIndex.put(KNOWN_ALIASES.get(norm), i);
            } else {
                dynamicHeaders.put(rawHeader, i);
                dynamicHeaderNames.add(rawHeader);
            }
        }

        if (!fieldToIndex.containsKey(COL_PHONE)) {
            throw new ValidationException(ErrorCode.CSV_PHONE_COLUMN_MISSING, header);
        }

        List<ParsedTarget> targets = new ArrayList<>();
        List<CsvRowError> errors = new ArrayList<>();
        for (int i = 1; i < lines.size(); i++) {
            String raw = lines.get(i);
            int lineNumber = i + 1;
            if (raw.isBlank()) {
                continue;
            }
            try {
                targets.add(toTarget(splitRow(raw, delimiter), fieldToIndex, dynamicHeaders, lineNumber));
            } catch (Exception e) {
                errors.add(new CsvRowError(lineNumber, e.getMessage()));
            }
        }
        return new TargetCsvParseResult(targets, errors, dynamicHeaderNames);
    }

    private static ParsedTarget toTarget(List<String> cells,
                                         Map<String, Integer> fieldToIndex,
                                         Map<String, Integer> dynamicHeaders,
                                         int line) {
        String phone = value(cells, fieldToIndex, COL_PHONE);
        if (phone == null || phone.isBlank()) {
            throw new ValidationException(ErrorCode.CSV_PHONE_EMPTY);
        }

        long clientId = 0;
        String rawClientId = value(cells, fieldToIndex, COL_CLIENT_ID);
        if (rawClientId != null && !rawClientId.isBlank()) {
            try {
                clientId = Long.parseLong(rawClientId.trim());
            } catch (NumberFormatException e) {
                // If not numeric, keep clientId as 0 and put into context
            }
        }

        ObjectNode context = MAPPER.createObjectNode();

        // 1. Populate known context fields (clientName, debtAmount, debtDay, dueDate, contractNumber, etc.)
        for (Map.Entry<String, Integer> entry : fieldToIndex.entrySet()) {
            String fieldName = entry.getKey();
            if (COL_PHONE.equals(fieldName) || COL_CLIENT_ID.equals(fieldName) || COL_LANGUAGE.equals(fieldName)) {
                continue;
            }
            int at = entry.getValue();
            if (at < cells.size()) {
                String cell = cells.get(at);
                if (cell != null && !cell.isBlank()) {
                    context.put(fieldName, cell.trim());
                }
            }
        }

        // 2. Populate any extra dynamic CSV columns into context_data as well
        for (Map.Entry<String, Integer> entry : dynamicHeaders.entrySet()) {
            String headerName = entry.getKey();
            int at = entry.getValue();
            if (at < cells.size()) {
                String cell = cells.get(at);
                if (cell != null && !cell.isBlank()) {
                    context.put(headerName, cell.trim());
                }
            }
        }

        String language = value(cells, fieldToIndex, COL_LANGUAGE);
        return new ParsedTarget(line, clientId, phone.trim(),
                language == null || language.isBlank() ? null : language.trim(),
                context.toString());
    }

    private static String value(List<String> cells, Map<String, Integer> index, String column) {
        Integer at = index.get(column);
        return at == null || at >= cells.size() ? null : cells.get(at);
    }

    private static String normalize(String header) {
        return header == null ? "" : header.trim().toLowerCase().replaceAll("[\\s_\\-]", "");
    }

    private static List<String> splitLines(String csv) {
        if (csv == null || csv.isBlank()) {
            return List.of();
        }
        List<String> lines = new ArrayList<>();
        for (String line : csv.replace("\r\n", "\n").replace('\r', '\n').split("\n")) {
            lines.add(line);
        }
        while (!lines.isEmpty() && lines.get(lines.size() - 1).isBlank()) {
            lines.remove(lines.size() - 1);
        }
        return lines;
    }

    private static char detectDelimiter(String header) {
        long commas = header.chars().filter(c -> c == ',').count();
        long semicolons = header.chars().filter(c -> c == ';').count();
        long tabs = header.chars().filter(c -> c == '\t').count();
        if (tabs > commas && tabs > semicolons) return '\t';
        return semicolons > commas ? ';' : ',';
    }

    private static List<String> splitRow(String row, char delimiter) {
        List<String> cells = new ArrayList<>();
        StringBuilder cell = new StringBuilder();
        boolean quoted = false;
        for (int i = 0; i < row.length(); i++) {
            char c = row.charAt(i);
            if (quoted) {
                if (c == '"') {
                    if (i + 1 < row.length() && row.charAt(i + 1) == '"') {
                        cell.append('"');
                        i++;
                    } else {
                        quoted = false;
                    }
                } else {
                    cell.append(c);
                }
            } else if (c == '"') {
                quoted = true;
            } else if (c == delimiter) {
                cells.add(cell.toString());
                cell.setLength(0);
            } else {
                cell.append(c);
            }
        }
        cells.add(cell.toString());
        return cells;
    }
}
