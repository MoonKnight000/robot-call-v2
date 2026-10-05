package uz.murodjon.robotcallv2.agent.dialog;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The word count is what decides whether a caller talking over the bot is taking the floor
 * outright, so a single Uzbek word must count as one and punctuation as none.
 */
class BackchannelsTest {

    @Test
    void punctuationAndCaseDoNotAddWords() {
        assertThat(Backchannels.words("Aha.")).containsExactly("aha");
        assertThat(Backchannels.words("  ha! ")).containsExactly("ha");
    }

    @Test
    void anApostropheDoesNotSplitAnUzbekWord() {
        assertThat(Backchannels.words("xo'p")).containsExactly("xop");
        assertThat(Backchannels.words("xoʻp")).containsExactly("xop");
    }

    @Test
    void aSentenceIsCountedWordByWord() {
        assertThat(Backchannels.words("Ha, ertaga to'layman")).containsExactly("ha", "ertaga", "tolayman");
    }
}
