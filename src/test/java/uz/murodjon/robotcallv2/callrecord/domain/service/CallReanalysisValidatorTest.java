package uz.murodjon.robotcallv2.callrecord.domain.service;

import org.junit.jupiter.api.Test;
import uz.murodjon.robotcallv2.shared.exception.ValidationException;

import java.time.Duration;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class CallReanalysisValidatorTest {

    private static final Instant FROM = Instant.parse("2026-07-01T00:00:00Z");

    @Test
    void aRangeOfExactlyTheMaximumIsAccepted() {
        assertThatCode(() -> CallReanalysisValidator.validateRange(FROM, FROM.plus(CallReanalysisValidator.MAX_RANGE)))
                .doesNotThrowAnyException();
    }

    @Test
    void aRangeLongerThanTheMaximumIsRefused() {
        Instant to = FROM.plus(CallReanalysisValidator.MAX_RANGE).plus(Duration.ofSeconds(1));

        assertThatThrownBy(() -> CallReanalysisValidator.validateRange(FROM, to))
                .isInstanceOf(ValidationException.class);
    }

    @Test
    void anEmptyOrBackwardRangeIsRefused() {
        assertThatThrownBy(() -> CallReanalysisValidator.validateRange(FROM, FROM))
                .isInstanceOf(ValidationException.class);
        assertThatThrownBy(() -> CallReanalysisValidator.validateRange(FROM, FROM.minusSeconds(1)))
                .isInstanceOf(ValidationException.class);
    }

    @Test
    void noCallsAndTooManyCallsAreRefused() {
        assertThatThrownBy(() -> CallReanalysisValidator.validateCallCount(0))
                .isInstanceOf(ValidationException.class);
        assertThatThrownBy(() -> CallReanalysisValidator.validateCallCount(CallReanalysisValidator.MAX_CALLS + 1))
                .isInstanceOf(ValidationException.class);
        assertThatCode(() -> CallReanalysisValidator.validateCallCount(CallReanalysisValidator.MAX_CALLS))
                .doesNotThrowAnyException();
    }
}
