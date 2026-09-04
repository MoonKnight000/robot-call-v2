package uz.murodjon.robotcallv2.agent.dialog;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class TurnRunnerSentenceTest {

    @Test
    void cutsShortAffirmationsImmediately() {
        StringBuilder pending = new StringBuilder("Aha. Sizga qanday yordam bera olaman?");
        String first = TurnRunner.takeSentence(pending);

        assertThat(first).isEqualTo("Aha.");
        assertThat(pending.toString()).isEqualTo(" Sizga qanday yordam bera olaman?");

        String second = TurnRunner.takeSentence(pending);
        assertThat(second).isEqualTo("Sizga qanday yordam bera olaman?");
        assertThat(pending.toString()).isEmpty();
    }

    @Test
    void cutsShortGreetingSentence() {
        StringBuilder pending = new StringBuilder("Salom! Qayerdansiz?");
        String first = TurnRunner.takeSentence(pending);

        assertThat(first).isEqualTo("Salom!");
        assertThat(pending.toString()).isEqualTo(" Qayerdansiz?");
    }

    @Test
    void doesNotSplitOnDecimalsOrFormattedSums() {
        StringBuilder pending = new StringBuilder("Qarzingiz 1.500.000 so'mni tashkil etadi.");
        String sentence = TurnRunner.takeSentence(pending);

        assertThat(sentence).isEqualTo("Qarzingiz 1.500.000 so'mni tashkil etadi.");
        assertThat(pending.toString()).isEmpty();
    }

    @Test
    void splitsOnLongCommaClausesForEarlyTtsStreaming() {
        StringBuilder pending = new StringBuilder("Assalomu alaykum hurmatli Murodjon aka, sizga bugun bankdan qo'ng'iroq qilyapmiz.");
        String firstClause = TurnRunner.takeSentence(pending);

        assertThat(firstClause).isEqualTo("Assalomu alaykum hurmatli Murodjon aka,");
        assertThat(pending.toString()).isEqualTo(" sizga bugun bankdan qo'ng'iroq qilyapmiz.");

        String secondClause = TurnRunner.takeSentence(pending);
        assertThat(secondClause).isEqualTo("sizga bugun bankdan qo'ng'iroq qilyapmiz.");
        assertThat(pending.toString()).isEmpty();
    }

    @Test
    void speculationCompatibilityMatchesExactAndPrefix() {
        // Exact normalized match
        assertThat(TurnRunner.isSpeculationCompatible("ha men murodjon", "Ha, men Murodjon.")).isTrue();

        // Interim prefix of final sentence
        assertThat(TurnRunner.isSpeculationCompatible("ha men murodjon", "Ha, men Murodjonman.")).isTrue();
        assertThat(TurnRunner.isSpeculationCompatible("eshityapman siz", "Eshityapman sizni.")).isTrue();

        // Different intent / speech
        assertThat(TurnRunner.isSpeculationCompatible("yo'q", "ha albatta")).isFalse();
        assertThat(TurnRunner.isSpeculationCompatible("salom", "xayr")).isFalse();
    }

    @Test
    void doesNotSplitOnAbbreviations() {
        StringBuilder pending = new StringBuilder("Sizning 1.5 mln. so'm qarzingiz bor.");
        String sentence = TurnRunner.takeSentence(pending);

        assertThat(sentence).isEqualTo("Sizning 1.5 mln. so'm qarzingiz bor.");
        assertThat(pending.toString()).isEmpty();
    }

    @Test
    void doesNotSplitOnCityAbbreviation() {
        StringBuilder pending = new StringBuilder("Idora Toshkent sh. Chilonzor tumanida joylashgan.");
        String sentence = TurnRunner.takeSentence(pending);

        assertThat(sentence).isEqualTo("Idora Toshkent sh. Chilonzor tumanida joylashgan.");
        assertThat(pending.toString()).isEmpty();
    }

    @Test
    void doesNotSplitOnDomainName() {
        StringBuilder pending = new StringBuilder("Iltimos, uysot.uz saytiga kiring.");
        String sentence = TurnRunner.takeSentence(pending);

        assertThat(sentence).isEqualTo("Iltimos, uysot.uz saytiga kiring.");
        assertThat(pending.toString()).isEmpty();
    }
}
