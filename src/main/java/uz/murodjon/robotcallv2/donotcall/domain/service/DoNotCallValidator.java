package uz.murodjon.robotcallv2.donotcall.domain.service;

import uz.murodjon.robotcallv2.shared.exception.ErrorCode;
import uz.murodjon.robotcallv2.shared.exception.ValidationException;

/** Pure domain validation rules for DNC operations. */
public final class DoNotCallValidator {

    private DoNotCallValidator() {
    }

    public static void validatePhone(String phone) {
        if (phone == null || phone.isBlank()) {
            throw new ValidationException(ErrorCode.PHONE_INVALID, "phone is required");
        }
    }
}
