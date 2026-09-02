package uz.murodjon.robotcallv2.inbound.domain.service;

import uz.murodjon.robotcallv2.shared.exception.ErrorCode;
import uz.murodjon.robotcallv2.shared.exception.ValidationException;

import java.time.LocalTime;

public final class InboundRouteValidator {

    private InboundRouteValidator() {
    }

    public static void validateBusinessHours(LocalTime start, LocalTime end) {
        if (start != null && end != null && start.equals(end)) {
            throw new ValidationException(ErrorCode.VALIDATION_FAILED, "businessHoursStart and businessHoursEnd cannot be identical");
        }
    }
}
