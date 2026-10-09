package uz.murodjon.robotcallv2.agent.dialog;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * A wrong "carry on" talks over a caller who meant to stop the bot — the worse of the two
 * mistakes — so every doubtful case must come back undecided, for the model.
 */
class FloorRulesTest {

    private static final String STATEMENT = "Sizning to'lovingiz 15-sanaga belgilangan.";
    private static final String QUESTION = "Bugun to'lay olasizmi?";

    @Test
    void listenerSoundsOverAStatementLetTheBotCarryOn() {
        assertThat(FloorRules.decide(STATEMENT, "ha")).isFalse();
        assertThat(FloorRules.decide(STATEMENT, "xo'p, xo'p")).isFalse();
        assertThat(FloorRules.decide(STATEMENT, "xoʻp")).isFalse();
        assertThat(FloorRules.decide(STATEMENT, "угу")).isFalse();
    }

    @Test
    void aYesOverAQuestionIsLeftToTheModel() {
        assertThat(FloorRules.decide(QUESTION, "ha")).isNull();
        assertThat(FloorRules.decide(QUESTION, "да")).isNull();
    }

    @Test
    void aNoTakesTheFloorOverAnyLine() {
        assertThat(FloorRules.decide(STATEMENT, "yo'q")).isTrue();
        assertThat(FloorRules.decide(QUESTION, "yo'q")).isTrue();
        assertThat(FloorRules.decide(STATEMENT, "ага, нет")).isTrue();
        assertThat(FloorRules.decide(STATEMENT, "to'xtang")).isTrue();
    }

    @Test
    void aQuestionTakesTheFloor() {
        assertThat(FloorRules.decide(STATEMENT, "qancha?")).isTrue();
        assertThat(FloorRules.decide(STATEMENT, "nima")).isTrue();
        assertThat(FloorRules.decide(STATEMENT, "почему")).isTrue();
    }

    @Test
    void otherWordsAreLeftToTheModel() {
        assertThat(FloorRules.decide(STATEMENT, "ertaga")).isNull();
        assertThat(FloorRules.decide(STATEMENT, "ha ertaga")).isNull();
        assertThat(FloorRules.decide(null, "ertaga")).isNull();
    }

    @Test
    void nothingSaidIsLeftToTheModel() {
        assertThat(FloorRules.decide(STATEMENT, "")).isNull();
        assertThat(FloorRules.decide(STATEMENT, null)).isNull();
    }
}
