package uz.murodjon.robotcallv2.contact.domain.service;

import uz.murodjon.robotcallv2.shared.exception.ErrorCode;
import uz.murodjon.robotcallv2.shared.exception.ValidationException;

import java.util.Map;
import java.util.regex.Pattern;

/** Pure domain validation rules for contacts. */
public final class ContactValidator {

    public static final int MAX_CUSTOM_FIELDS = 50;
    public static final int MAX_CUSTOM_FIELD_VALUE_LENGTH = 1000;

    private static final Pattern PHONE_PATTERN = Pattern.compile("^\\+?\\d{3,15}$");
    private static final Pattern CUSTOM_FIELD_KEY_PATTERN = Pattern.compile("^[A-Za-z_][A-Za-z0-9_]{0,63}$");

    private ContactValidator() {
    }

    public static void validatePhone(String phone) {
        if (phone == null || phone.isBlank()) {
            throw new ValidationException(ErrorCode.PHONE_INVALID, phone);
        }
        String normalized = phone.trim();
        if (!PHONE_PATTERN.matcher(normalized).matches()) {
            throw new ValidationException(ErrorCode.PHONE_INVALID, phone);
        }
    }

    /** At most {@link #MAX_CUSTOM_FIELDS} entries, each passing {@link #isValidCustomField}; null is valid. */
    public static void validateCustomFields(Map<String, String> customFields) {
        if (customFields == null) {
            return;
        }
        if (customFields.size() > MAX_CUSTOM_FIELDS) {
            throw new ValidationException(ErrorCode.CONTACT_CUSTOM_FIELDS_TOO_MANY, MAX_CUSTOM_FIELDS, customFields.size());
        }
        for (Map.Entry<String, String> field : customFields.entrySet()) {
            if (!isValidCustomField(field.getKey(), field.getValue())) {
                throw new ValidationException(ErrorCode.CONTACT_CUSTOM_FIELD_INVALID, field.getKey());
            }
        }
    }

    /** A key of letters, digits or _ (not starting with a digit, at most 64) and a non-null value of at most 1000 characters. */
    public static boolean isValidCustomField(String key, String value) {
        return key != null && CUSTOM_FIELD_KEY_PATTERN.matcher(key).matches()
                && value != null && value.length() <= MAX_CUSTOM_FIELD_VALUE_LENGTH;
    }
}
