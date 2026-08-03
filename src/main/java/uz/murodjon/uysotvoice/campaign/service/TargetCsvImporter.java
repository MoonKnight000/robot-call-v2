package uz.murodjon.uysotvoice.campaign.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;

import uz.murodjon.uysotvoice.campaign.dto.CsvColumnMapping;
import uz.murodjon.uysotvoice.campaign.dto.ParsedTarget;
import uz.murodjon.uysotvoice.campaign.dto.TargetCsvParseResult;
import uz.murodjon.uysotvoice.dialer.service.CallContextMapper;
import uz.murodjon.uysotvoice.shared.csv.CsvRowError;
import uz.murodjon.uysotvoice.shared.exception.ValidationException;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Parses a CSV of campaign targets (PROJECT.md §10 — "target ro'yxatini yuklash").
 *
 * <p>Targets could previously only be added one JSON object at a time. A debt-collection
 * campaign is a few thousand debtors exported from the CRM as a spreadsheet, and a
 * one-at-a-time API means either a script nobody wrote or a very long afternoon.
 *
 * <p>Two decisions worth stating. Columns are located <b>by header name</b>, not position,
 * because a CRM export's column order is not a contract and a silently shifted column would
 * put a debt amount in the phone field. And a bad row is <b>reported, not fatal</b>: an
 * import of 2 000 rows with three typos should load 1 997 and tell you about the three,
 * rather than reject the file and leave the operator to find them by eye.
 *
 * <p>Deliberately a small hand-written parser rather than a CSV library. It handles quoted
 * fields and embedded commas, which is what a spreadsheet export produces; anything more
 * exotic belongs in a proper library, and this file is the place that would change.
 */
public final class TargetCsvImporter {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    /** Columns that map to their own database field rather than into {@code context_data}. */
    private static final String COL_CLIENT_ID = "clientid";
    private static final String COL_PHONE = "phone";
    private static final String COL_LANGUAGE = "language";

    /**
     * The rest of the recognized headers land in {@code context_data} — the JSON the system
     * prompt reads its facts from ({@link CallContextMapper}). Mapped explicitly so a typo in
     * a header ("debtamout") is reported as an unknown column instead of being loaded as a
     * fact the agent will never mention.
     */
    private static final Map<String, String> CONTEXT_COLUMNS = Map.of(
            "clientname", "clientName",
            "debtamount", "debtAmount",
            "currency", "currency",
            "duedate", "dueDate",
            "contractnumber", "contractNumber",
            "goal", "goal");

    /** Every recognized normalized header, mapped to the field name shown in a mapping preview. */
    private static final Map<String, String> FIELD_NAMES;

    static {
        Map<String, String> m = new LinkedHashMap<>();
        m.put(COL_CLIENT_ID, "clientId");
        m.put(COL_PHONE, "phone");
        m.put(COL_LANGUAGE, "language");
        m.putAll(CONTEXT_COLUMNS);
        FIELD_NAMES = Map.copyOf(m);
    }

    private TargetCsvImporter() {
    }

    /**
     * Match each CSV header against a known field without parsing any data rows (§10.6
     * "ustunni moslashtirish" wizard step, {@code POST .../targets/csv/preview}).
     *
     * @throws ValidationException if the file is empty
     */
    public static List<CsvColumnMapping> mapColumns(String csv) {
        List<String> lines = splitLines(csv);
        if (lines.isEmpty()) {
            throw new ValidationException("CSV is empty");
        }
        char delimiter = detectDelimiter(lines.get(0));
        List<String> header = splitRow(lines.get(0), delimiter);
        List<CsvColumnMapping> mapping = new ArrayList<>();
        for (String h : header) {
            mapping.add(new CsvColumnMapping(h.trim(), FIELD_NAMES.get(normalize(h))));
        }
        return mapping;
    }

    /**
     * Parse {@code csv}. Never throws for bad data — a malformed row becomes a
     * {@link CsvRowError} — so the caller can insert what parsed and report the rest.
     *
     * @throws ValidationException if the file is empty or has no usable header
     */
    public static TargetCsvParseResult parse(String csv) {
        List<String> lines = splitLines(csv);
        if (lines.isEmpty()) {
            throw new ValidationException("CSV is empty");
        }
        char delimiter = detectDelimiter(lines.get(0));
        List<String> header = splitRow(lines.get(0), delimiter);
        Map<String, Integer> index = new LinkedHashMap<>();
        List<String> unknown = new ArrayList<>();
        for (int i = 0; i < header.size(); i++) {
            String key = normalize(header.get(i));
            if (key.isEmpty()) {
                continue;
            }
            if (isKnown(key)) {
                index.put(key, i);
            } else {
                unknown.add(header.get(i).trim());
            }
        }
        if (!index.containsKey(COL_PHONE)) {
            throw new ValidationException("CSV needs a 'phone' column; found: " + header);
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
                targets.add(toTarget(splitRow(raw, delimiter), index, lineNumber));
            } catch (Exception e) {
                errors.add(new CsvRowError(lineNumber, e.getMessage()));
            }
        }
        return new TargetCsvParseResult(targets, errors, unknown);
    }

    private static ParsedTarget toTarget(List<String> cells, Map<String, Integer> index, int line) {
        String phone = value(cells, index, COL_PHONE);
        if (phone == null || phone.isBlank()) {
            throw new ValidationException("phone is empty");
        }
        long clientId = 0;
        String rawClientId = value(cells, index, COL_CLIENT_ID);
        if (rawClientId != null && !rawClientId.isBlank()) {
            try {
                clientId = Long.parseLong(rawClientId.trim());
            } catch (NumberFormatException e) {
                throw new ValidationException("clientId '" + rawClientId + "' is not a number");
            }
        }

        ObjectNode context = MAPPER.createObjectNode();
        for (Map.Entry<String, String> column : CONTEXT_COLUMNS.entrySet()) {
            String cell = value(cells, index, column.getKey());
            if (cell != null && !cell.isBlank()) {
                context.put(column.getValue(), cell.trim());
            }
        }
        String language = value(cells, index, COL_LANGUAGE);
        return new ParsedTarget(line, clientId, phone.trim(),
                language == null || language.isBlank() ? null : language.trim(),
                context.toString());
    }

    private static boolean isKnown(String key) {
        return COL_CLIENT_ID.equals(key) || COL_PHONE.equals(key) || COL_LANGUAGE.equals(key)
                || CONTEXT_COLUMNS.containsKey(key);
    }

    private static String value(List<String> cells, Map<String, Integer> index, String column) {
        Integer at = index.get(column);
        return at == null || at >= cells.size() ? null : cells.get(at);
    }

    /** Header names are matched case- and separator-insensitively ("Debt Amount" == debtAmount). */
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
        // A trailing newline is normal in an exported file, not an empty row.
        while (!lines.isEmpty() && lines.get(lines.size() - 1).isBlank()) {
            lines.remove(lines.size() - 1);
        }
        return lines;
    }

    /**
     * Pick the delimiter from the header row: a semicolon is what a spreadsheet writes in
     * locales where the comma is the decimal separator, and silently mis-splitting such a
     * file is a common way to lose an import. Decided once from the header rather than per
     * row, so an unquoted semicolon inside a comma-separated field cannot change the shape
     * of one row.
     */
    private static char detectDelimiter(String header) {
        long commas = header.chars().filter(c -> c == ',').count();
        long semicolons = header.chars().filter(c -> c == ';').count();
        return semicolons > commas ? ';' : ',';
    }

    /**
     * Split one CSV row, honouring double quotes and the {@code ""} escape inside them.
     * A spreadsheet quotes any field containing the delimiter, which debtor names and notes
     * do.
     */
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
