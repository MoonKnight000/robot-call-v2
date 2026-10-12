package uz.murodjon.robotcallv2.agent.dialog;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * A wrong "carry on" talks over a caller who meant to stop the bot — the worse of the two
 * mistakes — so the words alone only ever decide "stop"; every other case comes back
 * undecided, for the model.
 */
class FloorRulesTest {

    @Test
    void listenerSoundsAreLeftToTheModel() {
        // "ha" over "Ertaga to'lay olasiz." is an answer, though the line has no "?".
        assertThat(FloorRules.decide("ha")).isNull();
        assertThat(FloorRules.decide("xo'p, xo'p")).isNull();
        assertThat(FloorRules.decide("mayli")).isNull();
        assertThat(FloorRules.decide("угу")).isNull();
        assertThat(FloorRules.decide("да")).isNull();
    }

    @Test
    void aNoTakesTheFloor() {
        assertThat(FloorRules.decide("yo'q")).isTrue();
        assertThat(FloorRules.decide("ага, нет")).isTrue();
        assertThat(FloorRules.decide("to'xtang")).isTrue();
    }

    @Test
    void aQuestionTakesTheFloor() {
        assertThat(FloorRules.decide("qancha?")).isTrue();
        assertThat(FloorRules.decide("nima")).isTrue();
        assertThat(FloorRules.decide("почему")).isTrue();
    }

    @Test
    void otherWordsAreLeftToTheModel() {
        assertThat(FloorRules.decide("ertaga")).isNull();
        assertThat(FloorRules.decide("ha ertaga")).isNull();
    }

    @Test
    void nothingSaidIsLeftToTheModel() {
        assertThat(FloorRules.decide("")).isNull();
        assertThat(FloorRules.decide(null)).isNull();
    }
}
