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
            "8600123456789012|sakkiz olti nol nol bir ikki uch to'rt besh olti yetti sakkiz to'qqiz nol bir ikki",
            // a phone number is twelve digits, i.e. inside the sum rule: read in the groups
            // it is said in, so the caller can check it against the number they know
            "+998901234567|to'qqiz yuz to'qson sakkiz, to'qson, yuz yigirma uch, qirq besh, oltmish yetti",
            "90 123 45 67|to'qson, yuz yigirma uch, qirq besh, oltmish yetti",
            // sums with trailing .00 and tiyin
            "1 500 000.00 so'm|bir million besh yuz ming so'm",
            "15000.50 so'm|o'n besh ming so'm ellik tiyin",
            // bank card numbers in groups of 4 digits
            "8600 1234 5678 9012|sakson olti nol nol, o'n ikki o'ttiz to'rt, ellik olti yetmish sakkiz, to'qson o'n ikki",
            "8600-1234-5678-9012|sakson olti nol nol, o'n ikki o'ttiz to'rt, ellik olti yetmish sakkiz, to'qson o'n ikki",
            // half quantities
            "1.5 million so'm|bir yarim million so'm",
            "2.5 kun|ikki yarim kun",
            "0.5 soat|yarim soat",
            // the hour a slot is booked for, not two figures either side of a colon
            "soat 14:30 da|soat o'n to'rt o'ttiz da",
            "9:00|to'qqiz",
            // separate numbers next to each other stay separate, not glued into one figure
            "15 000, 20 000 so'm|o'n besh ming, yigirma ming so'm",
            "500 000. 3 kun qoldi|besh yuz ming. uch kun qoldi",
            "3, 6 yoki 12 oy|uch, olti yoki o'n ikki oy",
            "2 3 kun|ikki uch kun",
            // decimals other than a half
            "12.75%|o'n ikki butun yuzdan yetmish besh foiz",
            "2.3 kun|ikki butun o'ndan uch kun"
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
    void dropsDeliveryTagsInsteadOfReadingThem() {
        assertThat(SpeechTextNormalizer.normalize("[sound concerned] Tushunaman, bu qiyin.", "uz-UZ"))
                .isEqualTo("Tushunaman, bu qiyin.");
        assertThat(SpeechTextNormalizer.normalize("Понимаю [sigh] давайте решим.", "ru-RU"))
                .isEqualTo("Понимаю давайте решим.");
    }

    @Test
    void leavesOtherLanguagesAlone() {
        String line = "1500000 test do 2026-07-01";
        assertThat(SpeechTextNormalizer.normalize(line, "fr-FR")).isEqualTo(line);
    }

    @Test
    void normalizesRussianCardsAndQuantities() {
        assertThat(SpeechTextNormalizer.normalize("Карта: 8600 1234 5678 9012", "ru-RU"))
                .isEqualTo("Карта: восемьдесят шесть ноль ноль, двенадцать тридцать четыре, пятьдесят шесть семьдесят восемь, девяносто двенадцать");
        assertThat(SpeechTextNormalizer.normalize("1.5 миллиона", "ru-RU"))
                .isEqualTo("полтора миллиона");
    }
}
