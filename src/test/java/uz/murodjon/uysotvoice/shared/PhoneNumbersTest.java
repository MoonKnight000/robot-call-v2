package uz.murodjon.uysotvoice.shared;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * A number from here is concatenated straight into an Asterisk dial string
 * ({@code PJSIP/<number>@<endpoint>}), so these cases guard a dial-string injection,
 * not just tidy formatting.
 */
class PhoneNumbersTest {

    @Test
    void stripsHumanFormatting() {
        assertThat(PhoneNumbers.require(" +998 (95) 369-20-29 ")).isEqualTo("+998953692029");
    }

    @Test
    void keepsPlainNumberUnchanged() {
        assertThat(PhoneNumbers.require("998953692029")).isEqualTo("998953692029");
    }

    @Test
    void acceptsInternalExtensions() {
        // 3-4 digit extensions ring the test softphone — they must stay dialable.
        assertThat(PhoneNumbers.require("600")).isEqualTo("600");
        assertThat(PhoneNumbers.require("6001")).isEqualTo("6001");
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
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void rejectsNull() {
        assertThatThrownBy(() -> PhoneNumbers.require(null))
                .isInstanceOf(IllegalArgumentException.class);
        assertThat(PhoneNumbers.isValid(null)).isFalse();
    }
}
