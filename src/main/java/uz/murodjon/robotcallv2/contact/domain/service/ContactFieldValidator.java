package uz.murodjon.robotcallv2.contact.domain.service;

import uz.murodjon.robotcallv2.contact.domain.entity.ContactField;
import uz.murodjon.robotcallv2.contact.domain.enums.ContactFieldType;
import uz.murodjon.robotcallv2.contact.domain.enums.ContactFieldUpdateMode;
import uz.murodjon.robotcallv2.shared.exception.ErrorCode;
import uz.murodjon.robotcallv2.shared.exception.ValidationException;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.format.DateTimeParseException;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

/** Pure rules for contact field definitions and the values they allow. */
public final class ContactFieldValidator {

    /** A company defines at most as many fields as a contact may hold. */
    public static final int MAX_FIELDS = ContactValidator.MAX_CUSTOM_FIELDS;
    public static final int MAX_OPTIONS = 100;

    private static final Pattern KEY_PATTERN = Pattern.compile("^[A-Za-z][A-Za-z0-9_]{0,63}$");
    private static final Pattern EXTRACTION_KEY_PATTERN = Pattern.compile("^[A-Za-z_][A-Za-z0-9_]{0,63}$");
    /** The contact's own columns, and the prefixes kept for the platform's variables. */
    private static final Set<String> RESERVED_KEYS = Set.of("name", "phone", "address", "tags", "notes", "id");
    private static final List<String> RESERVED_PREFIXES = List.of("contact", "external");

    private ContactFieldValidator() {
    }

    public static void validate(ContactField field) {
        String key = field.key();
        if (!isValidKey(key)) {
            throw new ValidationException(ErrorCode.CONTACT_FIELD_KEY_INVALID, key);
        }
        if (field.type() == null) {
            throw new ValidationException(ErrorCode.CONTACT_FIELD_TYPE_REQUIRED, key);
        }
        validateOptions(field);
        if (field.updateMode() == ContactFieldUpdateMode.ACCUMULATE && field.type() != ContactFieldType.TEXT) {
            throw new ValidationException(ErrorCode.CONTACT_FIELD_ACCUMULATE_TEXT_ONLY, key);
        }
        if (field.extractionKey() != null && !EXTRACTION_KEY_PATTERN.matcher(field.extractionKey()).matches()) {
            throw new ValidationException(ErrorCode.CONTACT_FIELD_EXTRACTION_KEY_INVALID, key, field.extractionKey());
        }
    }

    public static boolean isValidKey(String key) {
        if (key == null || !KEY_PATTERN.matcher(key).matches()) {
            return false;
        }
        String lower = key.toLowerCase(Locale.ROOT);
        return !RESERVED_KEYS.contains(lower) && RESERVED_PREFIXES.stream().noneMatch(lower::startsWith);
    }

    private static void validateOptions(ContactField field) {
        List<String> options = field.options();
        if (field.type() != ContactFieldType.ENUM) {
            if (!options.isEmpty()) {
                throw new ValidationException(ErrorCode.CONTACT_FIELD_OPTIONS_INVALID, field.key());
            }
            return;
        }
        Set<String> seen = new HashSet<>();
        boolean valid = !options.isEmpty() && options.size() <= MAX_OPTIONS && options.stream()
                .allMatch(option -> option != null && !option.isBlank()
                        && option.length() <= ContactValidator.MAX_CUSTOM_FIELD_VALUE_LENGTH && seen.add(option));
        if (!valid) {
            throw new ValidationException(ErrorCode.CONTACT_FIELD_OPTIONS_INVALID, field.key());
        }
    }

    /**
     * Every value whose key the company has defined must fit that field's type; keys without a
     * definition stay free text, as before contact fields existed.
     */
    public static void validateValues(List<ContactField> fields, Map<String, String> values) {
        if (values == null || values.isEmpty()) {
            return;
        }
        for (ContactField field : fields) {
            String value = values.get(field.key());
            if (value != null && !isValidValue(field, value)) {
                throw new ValidationException(ErrorCode.CONTACT_FIELD_VALUE_INVALID, field.key(), field.type());
            }
        }
    }

    /** Whether {@code value} is a well-formed value of the field's type. */
    public static boolean isValidValue(ContactField field, String value) {
        if (value == null) {
            return false;
        }
        String trimmed = value.strip();
        try {
            return switch (field.type()) {
                case TEXT -> true;
                case NUMBER -> {
                    new BigDecimal(trimmed);
                    yield true;
                }
                case BOOLEAN -> trimmed.equalsIgnoreCase("true") || trimmed.equalsIgnoreCase("false");
                case ENUM -> field.options().contains(trimmed);
                case DATE -> {
                    LocalDate.parse(trimmed);
                    yield true;
                }
                case DATETIME -> isDateTime(trimmed);
            };
        } catch (NumberFormatException | DateTimeParseException e) {
            return false;
        }
    }

    private static boolean isDateTime(String value) {
        try {
            Instant.parse(value);
            return true;
        } catch (DateTimeParseException e) {
            OffsetDateTime.parse(value);
            return true;
        }
    }
}
