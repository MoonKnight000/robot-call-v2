package uz.murodjon.robotcallv2.donotcall.domain.service;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;
import uz.murodjon.robotcallv2.shared.exception.ValidationException;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class DoNotCallValidatorTest {

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"   ", "\t\n"})
    void validatePhoneThrowsWhenBlankOrNull(String phone) {
        assertThatThrownBy(() -> DoNotCallValidator.validatePhone(phone))
                .isInstanceOf(ValidationException.class);
    }

    @Test
    void validatePhoneAcceptsValidPhone() {
        assertThatCode(() -> DoNotCallValidator.validatePhone("+998901234567"))
                .doesNotThrowAnyException();
    }
}
