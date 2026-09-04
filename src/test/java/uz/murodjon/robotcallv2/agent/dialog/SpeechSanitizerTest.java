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
        assertThat(SpeechSanitizer.isUnspeakable("[TIZIM: JORIY BOSQICH: DEBT_NOTICE]")).isTrue();
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

    @Test
    void rejectsJson() {
        assertThat(SpeechSanitizer.isUnspeakable("{\"reply\": \"Salom\"}")).isTrue();
        assertThat(SpeechSanitizer.isUnspeakable("[\"item1\", \"item2\"]")).isTrue();
    }

    @Test
    void allowsNaturalUzbekAndRussianSentences() {
        assertThat(SpeechSanitizer.isUnspeakable("Assalomu alaykum! Men Murodjon aka bilan gaplashayapmanmi?")).isFalse();
        assertThat(SpeechSanitizer.isUnspeakable("Tushunarli, to'lovni nima sababdan kechiktirdingiz?")).isFalse();
        assertThat(SpeechSanitizer.isUnspeakable("Здравствуйте! Когда сможете оплатить?")).isFalse();
        assertThat(SpeechSanitizer.isUnspeakable("Aha, xo'p bo'ladi.")).isFalse();
    }

    @Test
    void stripsTagsInSanitize() {
        String input = "<thought>Thinking about debt</thought>To'lovni qachon amalga oshirasiz?";
        assertThat(SpeechSanitizer.sanitize(input)).isEqualTo("Thinking about debtTo'lovni qachon amalga oshirasiz?");
    }
}
