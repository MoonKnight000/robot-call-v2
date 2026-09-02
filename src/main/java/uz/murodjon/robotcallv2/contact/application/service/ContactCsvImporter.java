package uz.murodjon.robotcallv2.contact.application.service;

import uz.murodjon.robotcallv2.contact.application.dto.ContactCsvParseResult;
import uz.murodjon.robotcallv2.contact.application.dto.ParsedContact;
import uz.murodjon.robotcallv2.shared.csv.CsvRowError;
import uz.murodjon.robotcallv2.shared.exception.ErrorCode;
import uz.murodjon.robotcallv2.shared.exception.ValidationException;

import java.util.*;

/**
 * Parses a CSV of contacts with multi-lingual auto-detection (PROJECT.md §10.8).
 */
public final class ContactCsvImporter {

    private static final String COL_NAME = "name";
    private static final String COL_PHONE = "phone";
    private static final String COL_ADDRESS = "address";
    private static final String COL_TAGS = "tags";
    private static final String COL_NOTES = "notes";

    private static final Map<String, String> KNOWN_ALIASES = new HashMap<>();

    static {
        // Name
        List.of("name", "ism", "fio", "fullname", "mijoz", "imya", "fio_klienta", "klient")
                .forEach(k -> KNOWN_ALIASES.put(k, COL_NAME));

        // Phone
        List.of("phone", "tel", "telefon", "telefonraqam", "raqam", "phonenumber", "nomer", "nomertelefona", "mobile", "mobil")
                .forEach(k -> KNOWN_ALIASES.put(k, COL_PHONE));

        // Address
        List.of("address", "manzil", "adres", "location", "shahar", "viloyat")
                .forEach(k -> KNOWN_ALIASES.put(k, COL_ADDRESS));

        // Tags
        List.of("tags", "teglar", "tegi", "tag", "teg", "category", "toifa")
                .forEach(k -> KNOWN_ALIASES.put(k, COL_TAGS));

        // Notes
        List.of("notes", "note", "izoh", "izohlar", "comment", "komment", "zametka")
                .forEach(k -> KNOWN_ALIASES.put(k, COL_NOTES));
    }

    private ContactCsvImporter() {
    }

    public static ContactCsvParseResult parse(String csv) {
        List<String> lines = splitLines(csv);
        if (lines.isEmpty()) {
            throw new ValidationException(ErrorCode.CSV_EMPTY);
        }
        char delimiter = detectDelimiter(lines.get(0));
        List<String> header = splitRow(lines.get(0), delimiter);
        Map<String, Integer> index = new LinkedHashMap<>();
        List<String> unknown = new ArrayList<>();
        for (int i = 0; i < header.size(); i++) {
            String norm = normalize(header.get(i));
            if (norm.isEmpty()) {
                continue;
            }
            if (KNOWN_ALIASES.containsKey(norm)) {
                index.put(KNOWN_ALIASES.get(norm), i);
            } else {
                unknown.add(header.get(i).trim());
            }
        }
        if (!index.containsKey(COL_PHONE)) {
            throw new ValidationException(ErrorCode.CSV_PHONE_COLUMN_MISSING, header);
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
            throw new ValidationException(ErrorCode.CSV_PHONE_EMPTY);
        }
        String name = value(cells, index, COL_NAME);
        if (name == null || name.isBlank()) {
            name = phone.trim(); // fallback name to phone if empty
        }
        return new ParsedContact(line, name.trim(), phone.trim(),
                blankToNull(value(cells, index, COL_ADDRESS)),
                blankToNull(value(cells, index, COL_TAGS)),
                blankToNull(value(cells, index, COL_NOTES)));
    }

    private static String blankToNull(String s) {
        return s == null || s.isBlank() ? null : s.trim();
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
