package uz.murodjon.robotcallv2.sms.domain.service;

import uz.murodjon.robotcallv2.shared.exception.ErrorCode;
import uz.murodjon.robotcallv2.shared.exception.ValidationException;

public final class SmsValidator {

    private SmsValidator() {
    }

    public static void validate(String phone, String message) {
        if (phone == null || phone.isBlank()) {
            throw new ValidationException(ErrorCode.PHONE_INVALID, "phone is required");
        }
        if (message == null || message.isBlank()) {
            throw new ValidationException(ErrorCode.SAY_TEXT_BLANK);
        }
    }
}
