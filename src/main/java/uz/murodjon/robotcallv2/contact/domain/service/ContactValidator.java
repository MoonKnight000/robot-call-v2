package uz.murodjon.robotcallv2.contact.domain.service;

import uz.murodjon.robotcallv2.shared.exception.ErrorCode;
import uz.murodjon.robotcallv2.shared.exception.ValidationException;

import java.util.regex.Pattern;

/** Pure domain validation rules for contacts. */
public final class ContactValidator {

    private static final Pattern PHONE_PATTERN = Pattern.compile("^\\+?\\d{3,15}$");

    private ContactValidator() {
    }

    public static void validatePhone(String phone) {
        if (phone == null || phone.isBlank()) {
            throw new ValidationException(ErrorCode.PHONE_INVALID, "phone is required");
        }
        String normalized = phone.trim();
        if (!PHONE_PATTERN.matcher(normalized).matches()) {
            throw new ValidationException(ErrorCode.PHONE_INVALID, phone);
        }
    }
}
