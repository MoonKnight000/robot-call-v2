package uz.murodjon.robotcallv2.agent.flow;

import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZonedDateTime;

import static org.assertj.core.api.Assertions.assertThat;

class PbxPhrasesTest {

    private static final ZoneId TASHKENT = ZoneId.of("Asia/Tashkent");
    /** A Monday. */
    private static final LocalDate MONDAY = LocalDate.of(2026, 9, 14);

    private static ZonedDateTime at(LocalDate date, int hour, int minute) {
        return date.atTime(hour, minute).atZone(TASHKENT);
    }

    @Test
    void afterHoursOpeningTomorrow() {
        String text = PbxPhrases.afterHours("uz-UZ", at(MONDAY, 19, 0), at(MONDAY.plusDays(1), 9, 0), null);

        assertThat(text).isEqualTo("Hozir ish vaqtimizdan tashqari. Ertaga soat to'qqizda ishni boshlaymiz.");
    }

    @Test
    void afterHoursOpeningLaterToday() {
        String text = PbxPhrases.afterHours("uz-UZ", at(MONDAY, 13, 10), at(MONDAY, 14, 0), null);

        assertThat(text).endsWith("Bugun soat o'n to'rtda ishni davom ettiramiz.");
    }

    @Test
    void afterHoursOverTheWeekendNamesTheDay() {
        String text = PbxPhrases.afterHours("uz-UZ", at(MONDAY.plusDays(4), 19, 0), at(MONDAY.plusWeeks(1), 9, 30), null);

        assertThat(text).endsWith("Dushanba kuni soat to'qqiz o'ttizda ishni boshlaymiz.");
    }

    @Test
    void afterHoursFarAheadNamesTheDate() {
        String text = PbxPhrases.afterHours("uz-UZ", at(MONDAY, 19, 0), at(MONDAY.plusDays(10), 9, 0), "Mustaqillik kuni");

        assertThat(text).startsWith("Bugun Mustaqillik kuni munosabati bilan dam olish kuni.");
        assertThat(text).contains("Yigirma to'rtinchi sentabr kuni soat to'qqizda");
    }

    @Test
    void afterHoursInRussian() {
        String text = PbxPhrases.afterHours("ru-RU", at(MONDAY, 19, 0), at(MONDAY.plusDays(1), 9, 0), null);

        assertThat(text).isEqualTo("Сейчас нерабочее время. Мы начнём работу завтра в девять ноль-ноль.");
    }

    @Test
    void afterHoursWithNothingAheadSaysOnlyThatItIsClosed() {
        assertThat(PbxPhrases.afterHours("uz-UZ", at(MONDAY, 19, 0), null, null))
                .isEqualTo("Hozir ish vaqtimizdan tashqari.");
    }

    @Test
    void queuePositionInWords() {
        assertThat(PbxPhrases.queuePosition(129, null, "uz-UZ"))
                .isEqualTo("Sizning navbatingiz — yuz yigirma to'qqizinchi.");
        assertThat(PbxPhrases.queuePosition(4, 5, "uz-UZ"))
                .isEqualTo("Sizning navbatingiz — to'rtinchi. Taxminiy kutish vaqti — besh daqiqa.");
        assertThat(PbxPhrases.queuePosition(1, 1, "uz-UZ"))
                .isEqualTo("Siz navbatda birinchisiz. Taxminiy kutish vaqti — bir daqiqadan kam.");
    }

    @Test
    void queuePositionInRussianAgreesTheMinutes() {
        assertThat(PbxPhrases.queuePosition(129, 2, "ru-RU"))
                .isEqualTo("Ваш номер в очереди — сто двадцать девятый. Примерное время ожидания — две минуты.");
        assertThat(PbxPhrases.queuePosition(3, 21, "ru-RU")).endsWith("двадцать одна минута.");
        assertThat(PbxPhrases.queuePosition(3, 5, "ru-RU")).endsWith("пять минут.");
        assertThat(PbxPhrases.queuePosition(3, 12, "ru-RU")).endsWith("двенадцать минут.");
    }

    @Test
    void operatorWhisperCarriesWhatTheOperatorNeeds() {
        String whisper = PbxPhrases.operatorWhisper("uz-UZ", "Sotuv", "998901234567", "Aliyev Botir", true, 130,
                "to'lov");

        assertThat(whisper).isEqualTo(
                "Kiruvchi qo'ng'iroq, navbat: Sotuv. VIP mijoz. Mijoz: Aliyev Botir. Tanlovi: to'lov. Ikki daqiqa kutdi.");
    }
}
