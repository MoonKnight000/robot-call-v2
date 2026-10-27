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
    void identifiesBackchannelsAccurately() {
        assertThat(FloorRules.isBackchannel("ha")).isTrue();
        assertThat(FloorRules.isBackchannel("aha")).isTrue();
        assertThat(FloorRules.isBackchannel("xo'p, xo'p")).isTrue();
        assertThat(FloorRules.isBackchannel("tushunarli")).isTrue();
        assertThat(FloorRules.isBackchannel("eshitvoman")).isTrue();
        assertThat(FloorRules.isBackchannel("gapiravering")).isTrue();
        assertThat(FloorRules.isBackchannel("mayli, bo'pti")).isTrue();
        assertThat(FloorRules.isBackchannel("да, понятно")).isTrue();
        assertThat(FloorRules.isBackchannel("хорошо, слушаю")).isTrue();

        // Non-backchannels
        assertThat(FloorRules.isBackchannel("to'xtang")).isFalse();
        assertThat(FloorRules.isBackchannel("ha, to'lamayman")).isFalse();
        assertThat(FloorRules.isBackchannel("nima deyapsiz?")).isFalse();
        assertThat(FloorRules.isBackchannel("")).isFalse();
        assertThat(FloorRules.isBackchannel(null)).isFalse();
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
    void aDialectNoTakesTheFloor() {
        assertThat(FloorRules.decide("yo'g'-e")).isTrue();
        assertThat(FloorRules.decide("yo'qe")).isTrue();
        assertThat(FloorRules.decide("yuq")).isTrue();
        assertThat(FloorRules.decide("йўқ")).isTrue();
    }

    @Test
    void aNegativeVerbTakesTheFloor() {
        assertThat(FloorRules.decide("to'lamayman")).isTrue();
        assertThat(FloorRules.decide("shoshmanglar")).isTrue();
        assertThat(FloorRules.decide("tushunmadim")).isTrue();
        assertThat(FloorRules.decide("bo'lmaydi")).isTrue();
    }

    @Test
    void anInflectedQuestionOrWaitTakesTheFloor() {
        assertThat(FloorRules.decide("qayerdan")).isTrue();
        assertThat(FloorRules.decide("bilasizmi")).isTrue();
        assertThat(FloorRules.decide("to'xtanglar")).isTrue();
        assertThat(FloorRules.decide("bir savolim bor")).isTrue();
        assertThat(FloorRules.decide("секундочку")).isTrue();
        assertThat(FloorRules.decide("operatorga ulang")).isTrue();
        assertThat(FloorRules.decide("adashibsiz")).isTrue();
    }

    @Test
    void wordsThatOnlyLookLikeTheFormsAreLeftToTheModel() {
        // "yoqadi" is "I like it", "gapiravering" is "go on", "hamma" ends in "ma" by itself.
        assertThat(FloorRules.decide("yoqadi")).isNull();
        assertThat(FloorRules.decide("gapiravering")).isNull();
        assertThat(FloorRules.decide("hamma")).isNull();
        assertThat(FloorRules.decide("tushundim")).isNull();
        assertThat(FloorRules.decide("bo'ladi")).isNull();
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
