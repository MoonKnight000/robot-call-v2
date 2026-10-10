package uz.murodjon.robotcallv2.siptrunk.domain.service;

import uz.murodjon.robotcallv2.shared.exception.ErrorCode;
import uz.murodjon.robotcallv2.shared.exception.ValidationException;

/**
 * Validates CallerIdPool domain constraints.
 */
public final class CallerIdPoolValidator {

    private CallerIdPoolValidator() {
    }

    public static void validatePoolName(String name) {
        if (name == null || name.isBlank()) {
            throw new ValidationException(ErrorCode.CALLER_ID_POOL_NAME_BLANK);
        }
    }
}
