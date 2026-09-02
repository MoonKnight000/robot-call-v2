package uz.murodjon.robotcallv2.sms.domain.service;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;
import uz.murodjon.robotcallv2.shared.exception.ValidationException;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class SmsValidatorTest {

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"   ", "\t\n"})
    void validateThrowsWhenPhoneIsBlank(String phone) {
        assertThatThrownBy(() -> SmsValidator.validate(phone, "Valid SMS text"))
                .isInstanceOf(ValidationException.class);
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"   ", "\t\n"})
    void validateThrowsWhenMessageIsBlank(String message) {
        assertThatThrownBy(() -> SmsValidator.validate("+998901234567", message))
                .isInstanceOf(ValidationException.class);
    }

    @Test
    void validatePassesForValidPhoneAndMessage() {
        assertThatCode(() -> SmsValidator.validate("+998901234567", "To'lov qabul qilindi."))
                .doesNotThrowAnyException();
    }
}
