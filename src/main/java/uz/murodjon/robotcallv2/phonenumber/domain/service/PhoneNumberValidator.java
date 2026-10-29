package uz.murodjon.robotcallv2.phonenumber.domain.service;

import uz.murodjon.robotcallv2.phonenumber.domain.entity.PhoneNumber;
import uz.murodjon.robotcallv2.shared.exception.ErrorCode;
import uz.murodjon.robotcallv2.shared.exception.ValidationException;

public final class PhoneNumberValidator {

    private PhoneNumberValidator() {
    }

    /** The number itself is already normalized by the caller and required by the record. */
    public static void validatePoolItem(PhoneNumber number) {
        if (number.maxSupportedChannels() < 1) {
            throw new ValidationException(ErrorCode.VALIDATION_FAILED);
        }
    }

    public static void validatePurchase(PhoneNumber number, int requestedChannels) {
        if (requestedChannels < 1 || requestedChannels > number.maxSupportedChannels()) {
            throw new ValidationException(ErrorCode.CONCURRENCY_LIMIT_OUT_OF_RANGE, number.maxSupportedChannels());
        }
    }
}
