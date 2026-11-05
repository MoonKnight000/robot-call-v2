package uz.murodjon.robotcallv2.shared.util;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;

class PersonNamesTest {

    @ParameterizedTest
    @ValueSource(strings = {"Murodjon", "Анна Ли", "Лидия Петрова", "O'g'iloy", "Callum Smith", "Mijozbek"})
    void acceptsPersonNames(String name) {
        assertThat(PersonNames.isUsable(name)).isTrue();
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {
            "Пропущенный звонок +998953692029", "Входящий звонок", "Lead #42", "Новый лид", "Без имени",
            "Missed call", "Noma'lum mijoz", "   "
    })
    void rejectsCrmPlaceholders(String name) {
        assertThat(PersonNames.isUsable(name)).isFalse();
    }

    @ParameterizedTest
    @CsvSource(quoteCharacter = '"', value = {
            "Anvar Jo'rayev, Anvar",
            "Jo'rayev Anvar, Anvar",
            "Karimova Dilnoza Rustam qizi, Dilnoza",
            "Лидия Петрова, Лидия",
            "Murodjon, Murodjon"
    })
    void findsTheFirstName(String fullName, String firstName) {
        assertThat(PersonNames.findFirstName(fullName)).isEqualTo(firstName);
    }

    @ParameterizedTest
    @CsvSource(quoteCharacter = '"', value = {
            "Anvar Jo'rayev, Anvar aka",
            "Karimova Dilnoza, Dilnoza opa",
            "Murodjon, Murodjon"
    })
    void addressesInUzbekByTheSurname(String fullName, String address) {
        assertThat(PersonNames.findUzbekAddress(fullName)).isEqualTo(address);
    }
}
