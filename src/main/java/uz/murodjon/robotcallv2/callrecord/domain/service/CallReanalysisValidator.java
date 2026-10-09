package uz.murodjon.robotcallv2.callrecord.domain.service;

import uz.murodjon.robotcallv2.shared.exception.ErrorCode;
import uz.murodjon.robotcallv2.shared.exception.ValidationException;

import java.time.Duration;
import java.time.Instant;

/** What a reanalysis may be asked for, before anything is counted or charged. */
public final class CallReanalysisValidator {

    /** The widest range one job may cover. */
    public static final Duration MAX_RANGE = Duration.ofDays(90);
    /** The most calls one job may go through: each is a paid model request. */
    public static final int MAX_CALLS = 5000;

    private CallReanalysisValidator() {
    }

    /** {@code from} before {@code to}, at most {@link #MAX_RANGE} apart. */
    public static void validateRange(Instant from, Instant to) {
        if (!from.isBefore(to) || Duration.between(from, to).compareTo(MAX_RANGE) > 0) {
            throw new ValidationException(ErrorCode.CALL_REANALYSIS_RANGE_INVALID, MAX_RANGE.toDays());
        }
    }

    /** At least one call and at most {@link #MAX_CALLS}. */
    public static void validateCallCount(long callCount) {
        if (callCount == 0) {
            throw new ValidationException(ErrorCode.CALL_REANALYSIS_NO_CALLS);
        }
        if (callCount > MAX_CALLS) {
            throw new ValidationException(ErrorCode.CALL_REANALYSIS_TOO_MANY_CALLS, callCount, MAX_CALLS);
        }
    }
}
