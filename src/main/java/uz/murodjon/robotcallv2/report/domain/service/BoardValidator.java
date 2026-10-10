package uz.murodjon.robotcallv2.report.domain.service;

import uz.murodjon.robotcallv2.shared.exception.ErrorCode;
import uz.murodjon.robotcallv2.shared.exception.ValidationException;

/**
 * Pure validator for analytics boards and widgets.
 */
public final class BoardValidator {

    private BoardValidator() {
    }

    public static void validateBoardName(String name) {
        if (name == null || name.isBlank()) {
            throw new ValidationException(ErrorCode.BOARD_NAME_BLANK);
        }
    }
}
