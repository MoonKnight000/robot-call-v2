package uz.murodjon.robotcallv2.agent.tts;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Every case here was heard on a real call: the sum, the due date and the contract number
 * are the three things a debt call exists to say, and each one came out of the provider as
 * punctuation read aloud.
 */
class SpeechTextNormalizerTest {

    @ParameterizedTest
    // Uzbek is full of apostrophes ("so'm", "to'rt"), which is @CsvSource's default quote
    // character — hence the explicit one.
    @CsvSource(delimiter = '|', quoteCharacter = '"', value = {
            // sums, with the separators a model writes them with
            "1500000 so'm|bir million besh yuz ming so'm",
            "1 500 000 so'm|bir million besh yuz ming so'm",
            "1.500.000 so'm|bir million besh yuz ming so'm",
            "3 kun|uch kun",
            "1200000000 so'm|bir milliard ikki yuz million so'm",
            // dates, as the model writes them and as a fact arrives
            "2026-yil 1-iyul|ikki ming yigirma oltinchi yil birinchi iyul",
            "7-avgustga|yettinchi avgustga",
            "2026-07-01|ikki ming yigirma oltinchi yil birinchi iyul",
            // a reference number is a label, not a quantity: digit by digit, no hyphens
            "UY-2026-00123-sonli|UY ikki nol ikki olti nol nol bir ikki uch sonli",
            // too long to be a sum — a card or account number, read out digit by digit
            "8600123456789012|sakkiz olti nol nol bir ikki uch to'rt besh olti yetti sakkiz to'qqiz nol bir ikki"
    })
    void writesDigitsOutInUzbek(String written, String spoken) {
        assertThat(SpeechTextNormalizer.normalize(written, "uz-UZ")).isEqualTo(spoken);
    }

    @Test
    void leavesTextWithoutDigitsAlone() {
        String line = "Assalomu alaykum, qanday yordam bera olaman?";
        assertThat(SpeechTextNormalizer.normalize(line, "uz-UZ")).isEqualTo(line);
    }

    @Test
    void leavesOtherLanguagesAlone() {
        String line = "1500000 test do 2026-07-01";
        assertThat(SpeechTextNormalizer.normalize(line, "fr-FR")).isEqualTo(line);
    }
}
