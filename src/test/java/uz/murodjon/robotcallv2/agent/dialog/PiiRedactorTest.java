package uz.murodjon.robotcallv2.agent.dialog;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;

class PiiRedactorTest {

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"   ", "\t\n"})
    void handlesNullOrBlankText(String input) {
        assertThat(PiiRedactor.redact(input)).isEqualTo(input);
    }

    @Test
    void leavesTextWithoutPiiUnmodified() {
        String input = "Assalomu alaykum, men bank xizmatlari bo'yicha qo'ng'iroq qilmoqdaman.";
        assertThat(PiiRedactor.redact(input)).isEqualTo(input);
    }

    @Test
    void redactsUzcardNumbers() {
        String input = "Mening Uzcard kartam 8600123456789012 raqami";
        String redacted = PiiRedactor.redact(input);
        assertThat(redacted).isEqualTo("Mening Uzcard kartam 8600 **** **** 9012 raqami");
    }

    @Test
    void redactsHumoNumbersWithSpaces() {
        String input = "Humo karta raqami: 9860 1234 5678 4321";
        String redacted = PiiRedactor.redact(input);
        assertThat(redacted).isEqualTo("Humo karta raqami: 9860 **** **** 4321");
    }

    @Test
    void redactsVisaAndMastercard() {
        String visa = "Visa: 4123-4567-8901-2345";
        String mc = "MasterCard: 5123 4567 8901 6789";

        assertThat(PiiRedactor.redact(visa)).isEqualTo("Visa: 4123 **** **** 2345");
        assertThat(PiiRedactor.redact(mc)).isEqualTo("MasterCard: 5123 **** **** 6789");
    }

    @Test
    void redactsUzbekPassportNumbers() {
        String input = "Pasport ma'lumotlarim AA 1234567 va ikkinchisi AB7654321";
        String redacted = PiiRedactor.redact(input);
        assertThat(redacted).isEqualTo("Pasport ma'lumotlarim AA ****567 va ikkinchisi AB ****321");
    }

    @Test
    void redactsPinfl14Digits() {
        String input = "JSHSHIR raqamim: 31201901234567";
        String redacted = PiiRedactor.redact(input);
        assertThat(redacted).isEqualTo("JSHSHIR raqamim: 3120 ****** 4567");
    }

    @Test
    void redactsCvvCodes() {
        String input = "Karta orqasidagi CVV: 789 va kodini 123";
        String redacted = PiiRedactor.redact(input);
        assertThat(redacted).isEqualTo("Karta orqasidagi CVV *** va CVV ***");
    }

    @Test
    void redactsMultiplePiiInSingleText() {
        String input = "Mening pasportim AA 9876543, kartam 8600 1111 2222 3333, CVV: 456 va PINFL 12345678901234";
        String redacted = PiiRedactor.redact(input);

        assertThat(redacted)
                .contains("AA ****543")
                .contains("8600 **** **** 3333")
                .contains("CVV ***")
                .contains("1234 ****** 1234");
    }
}
