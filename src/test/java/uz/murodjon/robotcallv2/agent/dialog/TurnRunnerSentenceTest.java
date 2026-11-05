package uz.murodjon.robotcallv2.agent.dialog;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class TurnRunnerSentenceTest {

    @Test
    void carriesShortAffirmationIntoTheNextSentence() {
        // "Aha." alone is under a second of audio and cannot cover the next round trip.
        StringBuilder pending = new StringBuilder("Aha. Sizga qanday yordam bera olaman? Qachon");
        String first = SentenceSplitter.takeSentence(pending);

        assertThat(first).isEqualTo("Aha. Sizga qanday yordam bera olaman?");
        assertThat(pending.toString()).isEqualTo(" Qachon");
    }

    @Test
    void waitsForMoreTextWhenEverythingSoFarIsShort() {
        StringBuilder pending = new StringBuilder("Salom! Qayerdansiz?");
        String first = SentenceSplitter.takeSentence(pending);

        assertThat(first).isNull();
        assertThat(pending.toString()).isEqualTo("Salom! Qayerdansiz?");
    }

    @Test
    void doesNotSplitOnDecimalsOrFormattedSums() {
        StringBuilder pending = new StringBuilder("Qarzingiz 1.500.000 so'mni tashkil etadi.");
        String sentence = SentenceSplitter.takeSentence(pending);

        assertThat(sentence).isEqualTo("Qarzingiz 1.500.000 so'mni tashkil etadi.");
        assertThat(pending.toString()).isEmpty();
    }

    @Test
    void keepsAnOrdinarySentenceWholeAcrossItsComma() {
        // Cut at the comma, "Murodjon aka," was spoken with a full stop's falling tone.
        StringBuilder pending = new StringBuilder("Assalomu alaykum hurmatli Murodjon aka, sizga bugun bankdan qo'ng'iroq qilyapmiz.");

        assertThat(SentenceSplitter.takeSentence(pending))
                .isEqualTo("Assalomu alaykum hurmatli Murodjon aka, sizga bugun bankdan qo'ng'iroq qilyapmiz.");
        assertThat(pending.toString()).isEmpty();
    }

    @Test
    void stillSplitsAVeryLongClauseForEarlyTtsStreaming() {
        String clause = "Sizning shartnomangiz bo'yicha o'tgan oy uchun belgilangan oylik to'lov muddati o'tib ketgan,";
        StringBuilder pending = new StringBuilder(clause + " iltimos, imkon qadar tezroq to'lang.");

        assertThat(SentenceSplitter.takeSentence(pending)).isEqualTo(clause);
    }

    @Test
    void speculationCompatibilityMatchesExactAndPrefix() {
        // Exact normalized match
        assertThat(SpeculationManager.isSpeculationCompatible("ha men murodjon", "Ha, men Murodjon.")).isTrue();

        // Interim prefix of final sentence
        assertThat(SpeculationManager.isSpeculationCompatible("ha men murodjon", "Ha, men Murodjonman.")).isTrue();
        assertThat(SpeculationManager.isSpeculationCompatible("eshityapman siz", "Eshityapman sizni.")).isTrue();

        // Different intent / speech
        assertThat(SpeculationManager.isSpeculationCompatible("yo'q", "ha albatta")).isFalse();
        assertThat(SpeculationManager.isSpeculationCompatible("salom", "xayr")).isFalse();
    }

    @Test
    void doesNotSplitOnAbbreviations() {
        StringBuilder pending = new StringBuilder("Sizning 1.5 mln. so'm qarzingiz bor.");
        String sentence = SentenceSplitter.takeSentence(pending);

        assertThat(sentence).isEqualTo("Sizning 1.5 mln. so'm qarzingiz bor.");
        assertThat(pending.toString()).isEmpty();
    }

    @Test
    void doesNotSplitOnCityAbbreviation() {
        StringBuilder pending = new StringBuilder("Idora Toshkent sh. Chilonzor tumanida joylashgan.");
        String sentence = SentenceSplitter.takeSentence(pending);

        assertThat(sentence).isEqualTo("Idora Toshkent sh. Chilonzor tumanida joylashgan.");
        assertThat(pending.toString()).isEmpty();
    }

    @Test
    void doesNotSplitOnDomainName() {
        StringBuilder pending = new StringBuilder("Iltimos, uysot.uz saytiga kiring.");
        String sentence = SentenceSplitter.takeSentence(pending);

        assertThat(sentence).isEqualTo("Iltimos, uysot.uz saytiga kiring.");
        assertThat(pending.toString()).isEmpty();
    }

    @Test
    void splitsOpeningGreetingImmediatelyWhenOpeningIsTrue() {
        StringBuilder pending = new StringBuilder("Assalomu alaykum! Sizga qanday yordam bera olaman?");
        // "Assalomu alaykum!" is 17 characters (> openingMinChars 14, < minChunkChars 20)
        String first = SentenceSplitter.takeSentence(pending, true);

        assertThat(first).isEqualTo("Assalomu alaykum!");
        assertThat(pending.toString()).isEqualTo(" Sizga qanday yordam bera olaman?");
    }

    @Test
    void cutsTheOpeningAtACommaOnceItHasEnoughWords() {
        StringBuilder pending = new StringBuilder("Xo'p, to'lov sanasini bilib olaylik, qachon to'lay olasiz?");

        assertThat(SentenceSplitter.takeSentence(pending, true)).isEqualTo("Xo'p, to'lov sanasini bilib olaylik,");
        assertThat(pending.toString()).isEqualTo(" qachon to'lay olasiz?");
    }

    @Test
    void keepsALaterPieceWholeAcrossTheSameComma() {
        StringBuilder pending = new StringBuilder("Xo'p, to'lov sanasini bilib olaylik, qachon to'lay olasiz?");

        assertThat(SentenceSplitter.takeSentence(pending)).isEqualTo("Xo'p, to'lov sanasini bilib olaylik, qachon to'lay olasiz?");
    }

    @Test
    void waitsForTheCharacterAfterAnOpeningCommaBeforeCutting() {
        // The stream may still turn "1," into "1,500".
        StringBuilder pending = new StringBuilder("Sizning qarzingiz hozircha taxminan 1,");

        assertThat(SentenceSplitter.takeSentence(pending, true)).isNull();
    }

    @Test
    void openingCommaWordCountIsConfigurable() {
        StringBuilder pending = new StringBuilder("Xo'p, to'lov sanasini bilib olaylik, qachon to'lay olasiz?");

        assertThat(SentenceSplitter.takeSentence(pending, true, new ChunkerProperties(0, 8, 0, 0)))
                .isEqualTo("Xo'p, to'lov sanasini bilib olaylik, qachon to'lay olasiz?");
    }

    @Test
    void splitsOnSemicolonWhenLengthSufficient() {
        StringBuilder pending = new StringBuilder("Hujjatlar tayyorlandi; ularni qabul qilib oling.");
        String sentence = SentenceSplitter.takeSentence(pending);

        assertThat(sentence).isEqualTo("Hujjatlar tayyorlandi;");
        assertThat(pending.toString()).isEqualTo(" ularni qabul qilib oling.");
    }
}
