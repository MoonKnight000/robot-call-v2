package uz.murodjon.uysotvoice.contact.service;

import uz.murodjon.uysotvoice.contact.dto.ContactCsvParseResult;
import uz.murodjon.uysotvoice.contact.dto.ParsedContact;
import uz.murodjon.uysotvoice.shared.csv.CsvRowError;
import uz.murodjon.uysotvoice.shared.exception.ValidationException;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Parses a CSV of contacts (§10.8 "⇧ CSV import").
 *
 * <p>A separate, small parser rather than a generalized {@code TargetCsvImporter} —
 * the column sets (name/phone/address/tags/notes vs. clientId/phone/language/debt
 * fields) are different enough that a shared abstraction would blur two independent
 * formats for a one-time code saving. See {@code TargetCsvImporter} for the header-
 * matched, report-bad-rows-don't-fail-the-file design this mirrors.
 */
public final class ContactCsvImporter {

    private static final String COL_NAME = "name";
    private static final String COL_PHONE = "phone";
    private static final String COL_ADDRESS = "address";
    private static final String COL_TAGS = "tags";
    private static final String COL_NOTES = "notes";

    private ContactCsvImporter() {
    }

    /**
     * Parse {@code csv}. Never throws for bad data — a malformed row becomes a
     * {@link CsvRowError} — so the caller can insert what parsed and report the rest.
     *
     * @throws ValidationException if the file is empty or has no usable header
     */
    public static ContactCsvParseResult parse(String csv) {
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

        List<ParsedContact> contacts = new ArrayList<>();
        List<CsvRowError> errors = new ArrayList<>();
        for (int i = 1; i < lines.size(); i++) {
            String raw = lines.get(i);
            int lineNumber = i + 1;
            if (raw.isBlank()) {
                continue;
            }
            try {
                contacts.add(toContact(splitRow(raw, delimiter), index, lineNumber));
            } catch (Exception e) {
                errors.add(new CsvRowError(lineNumber, e.getMessage()));
            }
        }
        return new ContactCsvParseResult(contacts, errors, unknown);
    }

    private static ParsedContact toContact(List<String> cells, Map<String, Integer> index, int line) {
        String phone = value(cells, index, COL_PHONE);
        if (phone == null || phone.isBlank()) {
            throw new ValidationException("phone is empty");
        }
        String name = value(cells, index, COL_NAME);
        if (name == null || name.isBlank()) {
            throw new ValidationException("name is empty");
        }
        return new ParsedContact(line, name.trim(), phone.trim(),
                blankToNull(value(cells, index, COL_ADDRESS)),
                blankToNull(value(cells, index, COL_TAGS)),
                blankToNull(value(cells, index, COL_NOTES)));
    }

    private static String blankToNull(String s) {
        return s == null || s.isBlank() ? null : s.trim();
    }

    private static boolean isKnown(String key) {
        return COL_NAME.equals(key) || COL_PHONE.equals(key) || COL_ADDRESS.equals(key)
                || COL_TAGS.equals(key) || COL_NOTES.equals(key);
    }

    private static String value(List<String> cells, Map<String, Integer> index, String column) {
        Integer at = index.get(column);
        return at == null || at >= cells.size() ? null : cells.get(at);
    }

    /** Header names are matched case- and separator-insensitively ("Phone Number" == phonenumber). */
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
