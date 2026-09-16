package uz.murodjon.robotcallv2.quality.domain.service;

import org.junit.jupiter.api.Test;
import uz.murodjon.robotcallv2.quality.domain.enums.CallReviewStatus;
import uz.murodjon.robotcallv2.shared.exception.ValidationException;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class CallReviewValidatorTest {

    @Test
    void pendingIsWhereACallWaitsNotAVerdict() {
        assertThatThrownBy(() -> CallReviewValidator.validateReview(CallReviewStatus.PENDING, null))
                .isInstanceOf(ValidationException.class);
    }

    @Test
    void theAgentWasWrongNeedsWhatItGotWrong() {
        assertThatThrownBy(() -> CallReviewValidator.validateReview(CallReviewStatus.AGENT_WRONG, " "))
                .isInstanceOf(ValidationException.class);
        assertThatCode(() -> CallReviewValidator.validateReview(CallReviewStatus.AGENT_WRONG, "narx to'qidi"))
                .doesNotThrowAnyException();
    }

    @Test
    void theAgentWasRightNeedsNoReason() {
        assertThatCode(() -> CallReviewValidator.validateReview(CallReviewStatus.AGENT_CORRECT, null))
                .doesNotThrowAnyException();
    }

    @Test
    void aRangeThatDoesNotMoveForwardIsRefused() {
        Instant now = Instant.now();
        assertThatThrownBy(() -> CallReviewValidator.validateRange(now, now))
                .isInstanceOf(ValidationException.class);
        assertThatCode(() -> CallReviewValidator.validateRange(now, null)).doesNotThrowAnyException();
    }
}
