package uz.murodjon.robotcallv2.wrapup.domain.service;

import uz.murodjon.robotcallv2.shared.exception.ConflictException;
import uz.murodjon.robotcallv2.shared.exception.ErrorCode;
import uz.murodjon.robotcallv2.shared.exception.ValidationException;
import uz.murodjon.robotcallv2.wrapup.domain.entity.WrapUpCode;

import java.time.Instant;

/** Whether an operator may close a call with a code. */
public final class WrapUpValidator {

    private WrapUpValidator() {
    }

    /**
     * A disabled code cannot be picked; a code that asks when to call again needs a moment
     * still ahead. A {@code callbackAt} sent with a code that does not ask for it is
     * allowed — the operator may note a time anyway.
     */
    public static void validate(WrapUpCode code, Instant callbackAt, Instant now) {
        if (!code.enabled()) {
            throw new ConflictException(ErrorCode.WRAP_UP_CODE_DISABLED, code.id());
        }
        if (code.requiresCallbackAt() && callbackAt == null) {
            throw new ValidationException(ErrorCode.WRAP_UP_CALLBACK_AT_REQUIRED, code.id());
        }
        if (callbackAt != null && !callbackAt.isAfter(now)) {
            throw new ValidationException(ErrorCode.WRAP_UP_CALLBACK_AT_IN_PAST, callbackAt);
        }
    }
}
