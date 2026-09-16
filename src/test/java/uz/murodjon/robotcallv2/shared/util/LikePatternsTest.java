package uz.murodjon.robotcallv2.shared.util;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class LikePatternsTest {

    @Test
    void wrapsPlainTextInWildcards() {
        assertThat(LikePatterns.buildContainsPattern("  998901  ")).isEqualTo("%998901%");
    }

    @Test
    void aBlankSearchMeansDoNotNarrow() {
        assertThat(LikePatterns.buildContainsPattern(null)).isNull();
        assertThat(LikePatterns.buildContainsPattern("   ")).isNull();
    }

    @Test
    void wildcardsTypedByTheUserAreMatchedLiterally() {
        assertThat(LikePatterns.buildContainsPattern("50%_off")).isEqualTo("%50!%!_off%");
    }

    @Test
    void theEscapeCharacterEscapesItself() {
        assertThat(LikePatterns.escape("hi!")).isEqualTo("hi!!");
    }
}
