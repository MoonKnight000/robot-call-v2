package uz.murodjon.robotcallv2.shared.util;

import org.junit.jupiter.params.ParameterizedTest;
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
}
