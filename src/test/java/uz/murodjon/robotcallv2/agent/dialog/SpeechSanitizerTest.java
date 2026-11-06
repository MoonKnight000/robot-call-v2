package uz.murodjon.robotcallv2.agent.dialog;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class SpeechSanitizerTest {

    @Test
    void rejectsNullOrBlank() {
        assertThat(SpeechSanitizer.isUnspeakable(null)).isTrue();
        assertThat(SpeechSanitizer.isUnspeakable("")).isTrue();
        assertThat(SpeechSanitizer.isUnspeakable("   ")).isTrue();
    }

    @Test
    void rejectsSystemNotesAndBrackets() {
        assertThat(SpeechSanitizer.isUnspeakable("[SYSTEM: CURRENT STAGE: DEBT_NOTICE]")).isTrue();
        assertThat(SpeechSanitizer.isUnspeakable("[TIZIM: Salomlashuv]")).isTrue();
        assertThat(SpeechSanitizer.isUnspeakable("[Tool natijasi]")).isTrue();
    }

    @Test
    void rejectsTemplatePlaceholders() {
        assertThat(SpeechSanitizer.isUnspeakable("{{clientName}}")).isTrue();
        assertThat(SpeechSanitizer.isUnspeakable("Salom {{name | do'stim}}")).isTrue();
    }

    @Test
    void rejectsSnakeCaseAndCodeTokens() {
        assertThat(SpeechSanitizer.isUnspeakable("dynamic_thought_or_fallback")).isTrue();
        assertThat(SpeechSanitizer.isUnspeakable("dynamic_thought")).isTrue();
        assertThat(SpeechSanitizer.isUnspeakable("reason_inquiry")).isTrue();
        assertThat(SpeechSanitizer.isUnspeakable("transitionTo('REASON_INQUIRY')")).isTrue();
        assertThat(SpeechSanitizer.isUnspeakable("null")).isTrue();
        assertThat(SpeechSanitizer.isUnspeakable("undefined")).isTrue();
    }

    /** The leak as it actually reached a caller: a tool or stage name inside a normal sentence. */
    @Test
    void rejectsToolAndStageNamesInsideASentence() {
        assertThat(SpeechSanitizer.isUnspeakable(
                "ibu kabi bosqichda transitionTo yoki recordRefusalReason kabilarni o'ylash kerak.")).isTrue();
        assertThat(SpeechSanitizer.isUnspeakable(
                "Hozircha DEBT_NOTICE dan PAYMENT_NEGOTIATION ga o'tishimiz kerak.")).isTrue();
        assertThat(SpeechSanitizer.isUnspeakable(
                "Dastlab transitionTo('PAYMENT_NEGOTIATION') chaqiramiz.")).isTrue();
    }

    @Test
    void rejectsJson() {
        assertThat(SpeechSanitizer.isUnspeakable("{\"reply\": \"Salom\"}")).isTrue();
        assertThat(SpeechSanitizer.isUnspeakable("[\"item1\", \"item2\"]")).isTrue();
    }

    /** JSON or a field name pasted into an otherwise ordinary sentence — not only a line that is all JSON. */
    @Test
    void rejectsJsonAndIdentifiersInsideASentence() {
        assertThat(SpeechSanitizer.isUnspeakable("Natija: {\"amount\": 500000, \"due\": \"2026-11-10\"}")).isTrue();
        assertThat(SpeechSanitizer.isUnspeakable("json { \"status\": \"ok\" }")).isTrue();
        assertThat(SpeechSanitizer.isUnspeakable("Sizning debt_amount qiymatingiz tekshirildi.")).isTrue();
        assertThat(SpeechSanitizer.isUnspeakable("Hozir get_balance orqali tekshiraman.")).isTrue();
    }

    @Test
    void keepsBracesAndColonsOfOrdinarySpeech() {
        assertThat(SpeechSanitizer.isUnspeakable("Eslatma: to'lov muddati ertaga tugaydi.")).isFalse();
        assertThat(SpeechSanitizer.isUnspeakable("Sizga (agar qulay bo'lsa) ertaga qo'ng'iroq qilaman.")).isFalse();
        assertThat(SpeechSanitizer.isUnspeakable("Saytimiz uysot.uz manzilida.")).isFalse();
    }

    @Test
    void stripsMarkdownSymbolsButKeepsTheWords() {
        assertThat(SpeechSanitizer.stripUnspeakableCharacters("**Muhim:** to'lov `ertaga` # kerak"))
                .isEqualTo("Muhim: to'lov ertaga  kerak");
    }

    @Test
    void allowsNaturalUzbekAndRussianSentences() {
        assertThat(SpeechSanitizer.isUnspeakable("Assalomu alaykum! Men Murodjon aka bilan gaplashayapmanmi?")).isFalse();
        assertThat(SpeechSanitizer.isUnspeakable("Tushunarli, to'lovni nima sababdan kechiktirdingiz?")).isFalse();
        assertThat(SpeechSanitizer.isUnspeakable("Здравствуйте! Когда сможете оплатить?")).isFalse();
        assertThat(SpeechSanitizer.isUnspeakable("Aha, xo'p bo'ladi.")).isFalse();
    }

    /** The character that reached a real caller's synthesizer; the rest of the line survives. */
    @Test
    void stripsUnspeakableCharactersButKeepsTheLine() {
        assertThat(SpeechSanitizer.stripUnspeakableCharacters("Xayr, salomat bo'ling!읍"))
                .isEqualTo("Xayr, salomat bo'ling!");
    }

    @Test
    void stripsGeminiThoughtMarkerGluedToSentenceOrAlone() {
        assertThat(SpeechSanitizer.stripUnspeakableCharacters("kelishdikmi, Murodjon aka?thought"))
                .isEqualTo("kelishdikmi, Murodjon aka?");
        assertThat(SpeechSanitizer.stripUnspeakableCharacters("thought")).isEmpty();
        assertThat(SpeechSanitizer.stripUnspeakableCharacters("a thoughtful reply")).isEqualTo("a thoughtful reply");
    }

    /** The tag a real call had synthesized in front of the line. */
    @Test
    void stripsAStageTagInFrontOfTheLine() {
        assertThat(SpeechSanitizer.stripUnspeakableCharacters("[SEARCH] Navoiy majmuasidan ko'rsataman."))
                .isEqualTo("Navoiy majmuasidan ko'rsataman.");
        assertThat(SpeechSanitizer.stripUnspeakableCharacters("Xo'p. [OFFER] Ertaga bog'lanamiz."))
                .isEqualTo("Xo'p. Ertaga bog'lanamiz.");
        assertThat(SpeechSanitizer.stripUnspeakableCharacters("2 xonali [taxminan] uy"))
                .isEqualTo("2 xonali [taxminan] uy");
    }

    /** The backup model's greeting tail on a simulated call: one word in two alphabets. */
    @Test
    void refusesAWordWrittenInTwoAlphabets() {
        assertThat(SpeechSanitizer.isUnspeakable("Hozir gaplashishga qulaymi? рооg'iroq qilyapman.")).isTrue();
        assertThat(SpeechSanitizer.isUnspeakable("Здравствуйте, Нигина! Это компания Uysot, по вашей квартире.")).isFalse();
        assertThat(SpeechSanitizer.isUnspeakable("Нигина opa, yaxshimisiz?")).isFalse();
    }

    /** A Russian-speaking caller answered in Uzbek; a short "da" or a brand name decides nothing. */
    @Test
    void tellsAReplyInTheOtherAlphabetFromTheCallers() {
        String russian = "да я помню про этот долг, были трудности";
        assertThat(SpeechSanitizer.isOtherScript("Dushanba kuni to'layman dedingiz, qaysi sana?", russian)).isTrue();
        assertThat(SpeechSanitizer.isOtherScript("Понимаю. Когда сможете оплатить?", russian)).isFalse();
        assertThat(SpeechSanitizer.isOtherScript("Нигина aka, yaxshimisiz?", russian)).isTrue();
        assertThat(SpeechSanitizer.isOtherScript("Здравствуйте, Murodjon! Это компания Uysot.", russian)).isFalse();
        assertThat(SpeechSanitizer.isOtherScript("Понимаю. Когда сможете оплатить?", "keyingi oy to'layman")).isTrue();
        assertThat(SpeechSanitizer.isOtherScript("Dushanba kuni to'layman dedingiz, qaysi sana?", "да")).isFalse();
        assertThat(SpeechSanitizer.isOtherScript("Xo'p, Uysot kompaniyasi.", null)).isFalse();
    }

    /** Eval #20: Uzbek in Cyrillic to a Russian caller passed because both were Cyrillic. */
    @Test
    void uzbekInCyrillicIsAnotherLanguageToARussianCaller() {
        String russian = "хорошо спасибо за понимание всего доброго до свидания";
        assertThat(SpeechSanitizer.isOtherScript("Хайр, саломат бўлинг!", russian)).isTrue();
        assertThat(SpeechSanitizer.isOtherScript("Всего доброго, до свидания!", russian)).isFalse();
        assertThat(SpeechSanitizer.isOtherScript("Хайр, саломат бўлинг!", "раҳмат, хайр, ҳаммаси яхши")).isFalse();
    }
}
