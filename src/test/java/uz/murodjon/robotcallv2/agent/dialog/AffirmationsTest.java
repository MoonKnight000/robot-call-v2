package uz.murodjon.robotcallv2.agent.dialog;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The yes that sends a write tool's request (UNIVERSAL-OPERATOR-PLAN B.3). The expensive
 * mistake is the loose one: a correction read as a yes sends the order the caller was just
 * changing.
 */
class AffirmationsTest {

    @ParameterizedTest
    @ValueSource(strings = {
            "Ha",
            "ha, to'g'ri",
            "Xo'p aka",
            "ha, hammasi to‘g‘ri",   // typographic apostrophes, as a transcriber writes them
            "tasdiqlayman",
            "Да, верно",
    })
    void aPlainYesConfirms(String text) {
        assertThat(Affirmations.saidByCaller(text)).isTrue();
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "yo'q",
            "ha, lekin 2 ta",        // a correction that starts with a yes
            "3 ta emas, 2 ta",
            "нет",
            "Chilonzor 5",
            "ha ha ha ha ha",        // too long to be nothing but a yes
    })
    void anythingElseDoesNot(String text) {
        assertThat(Affirmations.saidByCaller(text)).isFalse();
    }

    @Test
    void nothingSaidIsNotAYes() {
        assertThat(Affirmations.saidByCaller(null)).isFalse();
        assertThat(Affirmations.saidByCaller("")).isFalse();
    }
}
