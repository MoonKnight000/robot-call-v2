package uz.murodjon.robotcallv2.agent.dialog;

import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.UserMessage;

import java.util.List;

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

        // The final adds words: new information the guessed reply never saw
        assertThat(SpeculationManager.isSpeculationCompatible("да я помню про этот долг",
                "Да, я помню про этот долг, извините, были небольшие финансовые трудности.")).isFalse();
        assertThat(SpeculationManager.isSpeculationCompatible("keyingi oy",
                "Keyingi oy o'n beshinchi sanada to'layman.")).isFalse();
        // ...unless what it adds is a bare filler
        assertThat(SpeculationManager.isSpeculationCompatible("ha bor", "Ha bor, ha.")).isTrue();
    }

    @Test
    void theLastSpokenAgentLineDecidesWhetherTheCallerOwesAnAnswer() {
        List<Message> history = List.of(
                new UserMessage("ishlarim yurishmayapti"),
                new AssistantMessage("Tushunaman. Qaysi sanada to'laysiz?"),
                new AssistantMessage(""));

        assertThat(TurnRunner.findLastAgentLine(history)).isEqualTo("Tushunaman. Qaysi sanada to'laysiz?");
        assertThat(TurnRunner.endsWithQuestion(TurnRunner.findLastAgentLine(history))).isTrue();
        assertThat(TurnRunner.endsWithQuestion("Xo'p, salomat bo'ling.")).isFalse();
        assertThat(TurnRunner.findLastAgentLine(List.of())).isNull();
    }

    @Test
    void theLineAfterAJobIsToldWhatWasJustSaid() {
        assertThat(TurnRunner.describeLineJustSpoken("Bo'ldi, 13-noyabrga yozib qo'ydim."))
                .contains("\"Bo'ldi, 13-noyabrga yozib qo'ydim.\"").contains("Do not repeat");
        assertThat(TurnRunner.describeLineJustSpoken(null)).isEmpty();
    }

    @Test
    void speculationSurvivesTheFinalSpellingTheSameWordsApart() {
        // Yandex: the interim writes "koʻramiz", the final "ko ramiz" — the same words.
        assertThat(SpeculationManager.isSpeculationCompatible(
                "dekabrning boshlarida koʻramiz endi", "dekabrning boshlarida ko ramiz endi")).isTrue();
        assertThat(SpeculationManager.isSpeculationCompatible("toʻlayman", "to'layman")).isTrue();
        // ...but a final that adds words after the respelled ones still says something new
        assertThat(SpeculationManager.isSpeculationCompatible(
                "boshlarida koʻramiz", "boshlarida ko ramiz yigirmanchida")).isFalse();
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
