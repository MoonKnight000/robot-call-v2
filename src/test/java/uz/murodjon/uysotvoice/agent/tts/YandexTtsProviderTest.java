package uz.murodjon.uysotvoice.agent.tts;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Yandex v3 rejects a synthesis request over {@link YandexTtsProvider#MAX_TEXT_CHARS}
 * outright ("Too long text"), and on a live call that cost the caller the whole line —
 * the number normalizer can push a single tool-carried reply over the limit. The split
 * has to keep every word and never produce a piece Yandex would refuse.
 */
class YandexTtsProviderTest {

    @Test
    void shortTextComesBackAsTheSinglePieceItWas() {
        assertThat(YandexTtsProvider.splitForSynthesis("Assalomu alaykum."))
                .containsExactly("Assalomu alaykum.");
    }

    @Test
    void longTextIsSplitAtSentenceEndsWithinTheLimit() {
        String first = "Bu birinchi gap bo'lib, u ancha uzun va batafsil yozilgan. ".repeat(4).trim();
        String second = "Endi ikkinchi qism keladi va u ham o'z gapi bilan tugaydi.";
        List<String> pieces = YandexTtsProvider.splitForSynthesis(first + " " + second);

        assertThat(pieces).hasSizeGreaterThan(1);
        assertThat(pieces).allSatisfy(piece -> {
            assertThat(piece.length()).isLessThanOrEqualTo(YandexTtsProvider.MAX_TEXT_CHARS);
            assertThat(piece).isNotBlank();
        });
        // Nothing lost, nothing reordered: the pieces re-join into the original words.
        assertThat(String.join(" ", pieces)).isEqualTo(first + " " + second);
    }

    @Test
    void textWithNoSentenceEndSplitsOnWordBoundaries() {
        String words = "so'z ".repeat(80).trim(); // 399 chars, no terminator anywhere
        List<String> pieces = YandexTtsProvider.splitForSynthesis(words);

        assertThat(pieces).hasSize(2);
        assertThat(pieces).allSatisfy(piece ->
                assertThat(piece.length()).isLessThanOrEqualTo(YandexTtsProvider.MAX_TEXT_CHARS));
        assertThat(String.join(" ", pieces)).isEqualTo(words);
    }

    @Test
    void unbrokenRunLongerThanTheLimitIsHardCutRatherThanLooping() {
        String run = "a".repeat(YandexTtsProvider.MAX_TEXT_CHARS * 2 + 10);
        List<String> pieces = YandexTtsProvider.splitForSynthesis(run);

        assertThat(pieces).hasSize(3);
        assertThat(String.join("", pieces)).isEqualTo(run);
    }
}
