package uz.murodjon.robotcallv2.agent.dialog;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The cost of the two mistakes here is not symmetric. Missing an "aha" costs one
 * non-sequitur; swallowing "yo'q" or "to'ladim" loses the caller's actual answer and the
 * bot asks again as if they had said nothing. So what these pin down is mostly what is
 * <em>not</em> a backchannel.
 */
class BackchannelsTest {

    private static final int MIN_WORDS = 2;

    @Test
    void agreementOverTheBotIsOne() {
        assertThat(Backchannels.matches("aha", MIN_WORDS)).isTrue();
        assertThat(Backchannels.matches("ha", MIN_WORDS)).isTrue();
        assertThat(Backchannels.matches("угу", MIN_WORDS)).isTrue();
    }

    @Test
    void punctuationAndCaseDoNotHideIt() {
        // Both recognizers punctuate, and neither is consistent about it.
        assertThat(Backchannels.matches("Aha.", MIN_WORDS)).isTrue();
        assertThat(Backchannels.matches("  ha! ", MIN_WORDS)).isTrue();
    }

    @Test
    void anApostropheDoesNotSplitAnUzbekWord() {
        // "xo'p" is one word; treating the apostrophe as a break made it two, and two
        // words are never a backchannel.
        assertThat(Backchannels.matches("xo'p", MIN_WORDS)).isTrue();
        assertThat(Backchannels.matches("xoʻp", MIN_WORDS)).isTrue();
    }

    @Test
    void aOneWordAnswerIsStillAnAnswer() {
        // The reason the word list exists at all: these are as short as "aha" and they
        // are the whole reply.
        assertThat(Backchannels.matches("yo'q", MIN_WORDS)).isFalse();
        assertThat(Backchannels.matches("to'ladim", MIN_WORDS)).isFalse();
        assertThat(Backchannels.matches("нет", MIN_WORDS)).isFalse();
    }

    @Test
    void agreementThatStartsASentenceIsSpeech() {
        // The reason the word count exists at all: the "ha" here is not the message.
        assertThat(Backchannels.matches("ha, ertaga to'layman", MIN_WORDS)).isFalse();
        assertThat(Backchannels.matches("aha yaxshi", MIN_WORDS)).isFalse();
    }

    @Test
    void nothingIsIgnoredWhenTheFilterIsOff() {
        assertThat(Backchannels.matches("aha", 0)).isFalse();
        assertThat(Backchannels.matches("aha", 1)).isFalse();
    }

    @Test
    void emptyAndNullAreNotBackchannels() {
        assertThat(Backchannels.matches(null, MIN_WORDS)).isFalse();
        assertThat(Backchannels.matches("   ", MIN_WORDS)).isFalse();
        assertThat(Backchannels.matches("...", MIN_WORDS)).isFalse();
    }
}
