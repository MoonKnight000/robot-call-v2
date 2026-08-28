package uz.murodjon.uysotvoice.shared.util;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import uz.murodjon.uysotvoice.shared.exception.ValidationException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Unit tests for phone number normalization, validation and formatting using Google's libphonenumber.
 */
class PhoneNumbersTest {

    @Test
    void normalizesUzbekNumbersWithFormatting() {
        assertThat(PhoneNumbers.require(" +998 (95) 369-20-29 ")).isEqualTo("+998953692029");
        assertThat(PhoneNumbers.require("998953692029")).isEqualTo("+998953692029");
        assertThat(PhoneNumbers.require("95 369 20 29")).isEqualTo("+998953692029");
        assertThat(PhoneNumbers.require("953692029")).isEqualTo("+998953692029");
        assertThat(PhoneNumbers.require("+998901234567")).isEqualTo("+998901234567");
    }

    @Test
    void normalizesInternationalNumbers() {
        assertThat(PhoneNumbers.require("+1 (650) 253-0000")).isEqualTo("+16502530000");
        assertThat(PhoneNumbers.require("+7 999 123-45-67")).isEqualTo("+79991234567");
    }

    @Test
    void acceptsInternalExtensions() {
        // 3-4 digit extensions ring the test softphone — they must stay dialable.
        assertThat(PhoneNumbers.require("600")).isEqualTo("600");
        assertThat(PhoneNumbers.require("6001")).isEqualTo("6001");
    }

    @Test
    void formatsForDisplay() {
        assertThat(PhoneNumbers.formatInternational("953692029")).isEqualTo("+998 95 369 20 29");
        assertThat(PhoneNumbers.formatNational("+998953692029")).isEqualTo("(95) 369-20-29");
        assertThat(PhoneNumbers.formatInternational("600")).isEqualTo("600");
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "600@evil-endpoint",   // would redirect the call to another endpoint
            "600&600",             // Asterisk dial-string separator
            "PJSIP/600",
            "12",                  // too short to be a real destination
            "1234567890123456",    // beyond E.164's 15 digits
            "abc",
            "",
            "   ",
    })
    void rejectsAnythingNotDialable(String raw) {
        assertThatThrownBy(() -> PhoneNumbers.require(raw))
                .isInstanceOf(ValidationException.class);
    }
}
