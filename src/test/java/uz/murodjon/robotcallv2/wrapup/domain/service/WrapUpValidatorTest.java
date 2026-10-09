package uz.murodjon.robotcallv2.wrapup.domain.service;

import org.junit.jupiter.api.Test;
import uz.murodjon.robotcallv2.shared.exception.AppException;
import uz.murodjon.robotcallv2.shared.exception.ConflictException;
import uz.murodjon.robotcallv2.shared.exception.ErrorCode;
import uz.murodjon.robotcallv2.shared.exception.ValidationException;
import uz.murodjon.robotcallv2.wrapup.domain.entity.WrapUpCode;
import uz.murodjon.robotcallv2.wrapup.domain.enums.WrapUpOutcome;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class WrapUpValidatorTest {

    private static final Instant NOW = Instant.parse("2026-10-06T10:00:00Z");

    @Test
    void disabledCodeCannotBePicked() {
        assertThatThrownBy(() -> WrapUpValidator.validate(code(false, false), null, NOW))
                .isInstanceOf(ConflictException.class)
                .extracting(e -> ((AppException) e).code())
                .isEqualTo(ErrorCode.WRAP_UP_CODE_DISABLED);
    }

    @Test
    void codeAskingForACallbackNeedsOne() {
        assertThatThrownBy(() -> WrapUpValidator.validate(code(true, true), null, NOW))
                .isInstanceOf(ValidationException.class)
                .extracting(e -> ((AppException) e).code())
                .isEqualTo(ErrorCode.WRAP_UP_CALLBACK_AT_REQUIRED);
    }

    @Test
    void callbackCannotBeInThePast() {
        assertThatThrownBy(() -> WrapUpValidator.validate(code(true, true), NOW, NOW))
                .isInstanceOf(ValidationException.class)
                .extracting(e -> ((AppException) e).code())
                .isEqualTo(ErrorCode.WRAP_UP_CALLBACK_AT_IN_PAST);
    }

    @Test
    void futureCallbackIsAccepted() {
        assertThatCode(() -> WrapUpValidator.validate(code(true, true), NOW.plusSeconds(3600), NOW))
                .doesNotThrowAnyException();
    }

    @Test
    void codeWithoutCallbackNeedsNone() {
        assertThatCode(() -> WrapUpValidator.validate(code(true, false), null, NOW)).doesNotThrowAnyException();
    }

    private static WrapUpCode code(boolean enabled, boolean requiresCallbackAt) {
        return new WrapUpCode(1L, 1L, "Qayta qo'ng'iroq", WrapUpOutcome.RETRY, requiresCallbackAt, enabled, NOW);
    }
}
