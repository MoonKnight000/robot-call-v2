package uz.murodjon.robotcallv2.contact.domain.service;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;
import uz.murodjon.robotcallv2.shared.exception.ValidationException;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ContactValidatorTest {

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"   ", "\t\n"})
    void validatePhoneThrowsWhenBlankOrNull(String phone) {
        assertThatThrownBy(() -> ContactValidator.validatePhone(phone))
                .isInstanceOf(ValidationException.class);
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "invalid_phone",
            "12",
            "+99890123456789012345",
            "998 90 123 45 67 abc"
    })
    void validatePhoneThrowsForInvalidFormats(String phone) {
        assertThatThrownBy(() -> ContactValidator.validatePhone(phone))
                .isInstanceOf(ValidationException.class);
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "+998901234567",
            "998901234567",
            "12345",
            "+12025550123"
    })
    void validatePhoneAcceptsValidFormats(String phone) {
        assertThatCode(() -> ContactValidator.validatePhone(phone))
                .doesNotThrowAnyException();
    }
}
