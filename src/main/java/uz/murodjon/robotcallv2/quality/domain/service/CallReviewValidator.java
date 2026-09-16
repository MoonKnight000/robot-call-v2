package uz.murodjon.robotcallv2.quality.domain.service;

import uz.murodjon.robotcallv2.quality.domain.enums.CallReviewStatus;
import uz.murodjon.robotcallv2.shared.exception.ErrorCode;
import uz.murodjon.robotcallv2.shared.exception.ValidationException;

import java.time.Instant;

public final class CallReviewValidator {

    private CallReviewValidator() {
    }

    /**
     * A review is a verdict: {@code PENDING} is where a call waits, not something a person sets.
     * "The agent was wrong" says nothing to the next person without what it got wrong.
     */
    public static void validateReview(CallReviewStatus status, String reason) {
        if (status == null || status == CallReviewStatus.PENDING) {
            throw new ValidationException(ErrorCode.CALL_REVIEW_STATUS_INVALID, status);
        }
        if (status == CallReviewStatus.AGENT_WRONG && (reason == null || reason.isBlank())) {
            throw new ValidationException(ErrorCode.CALL_REVIEW_REASON_REQUIRED);
        }
    }

    public static void validateRange(Instant from, Instant to) {
        if (from != null && to != null && !from.isBefore(to)) {
            throw new ValidationException(ErrorCode.DATE_RANGE_INVALID);
        }
    }
}
