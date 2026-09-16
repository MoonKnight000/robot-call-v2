package uz.murodjon.robotcallv2.agent.tts;

import org.junit.jupiter.api.Test;

import java.time.LocalTime;

import static org.assertj.core.api.Assertions.assertThat;

class SpokenNumbersTest {

    @Test
    void uzbekOrdinals() {
        assertThat(SpokenNumbers.ordinal(1, "uz-UZ")).isEqualTo("birinchi");
        assertThat(SpokenNumbers.ordinal(2, "uz-UZ")).isEqualTo("ikkinchi");
        assertThat(SpokenNumbers.ordinal(20, "uz-UZ")).isEqualTo("yigirmanchi");
        assertThat(SpokenNumbers.ordinal(129, "uz-UZ")).isEqualTo("yuz yigirma to'qqizinchi");
    }

    @Test
    void russianOrdinals() {
        assertThat(SpokenNumbers.ordinal(1, "ru-RU")).isEqualTo("первый");
        assertThat(SpokenNumbers.ordinal(3, "ru-RU")).isEqualTo("третий");
        assertThat(SpokenNumbers.ordinal(12, "ru-RU")).isEqualTo("двенадцатый");
        assertThat(SpokenNumbers.ordinal(20, "ru-RU")).isEqualTo("двадцатый");
        assertThat(SpokenNumbers.ordinal(21, "ru-RU")).isEqualTo("двадцать первый");
        assertThat(SpokenNumbers.ordinal(40, "ru-RU")).isEqualTo("сороковой");
        assertThat(SpokenNumbers.ordinal(100, "ru-RU")).isEqualTo("сотый");
        assertThat(SpokenNumbers.ordinal(129, "ru-RU")).isEqualTo("сто двадцать девятый");
        assertThat(SpokenNumbers.ordinal(200, "ru-RU")).isEqualTo("двухсотый");
        assertThat(SpokenNumbers.ordinal(1000, "ru-RU")).isEqualTo("тысячный");
    }

    @Test
    void englishOrdinalsKeepTheDigits() {
        assertThat(SpokenNumbers.ordinal(1, "en-US")).isEqualTo("1st");
        assertThat(SpokenNumbers.ordinal(12, "en-US")).isEqualTo("12th");
        assertThat(SpokenNumbers.ordinal(22, "en-US")).isEqualTo("22nd");
        assertThat(SpokenNumbers.ordinal(113, "en-US")).isEqualTo("113th");
    }

    @Test
    void clockTimes() {
        assertThat(SpokenNumbers.clockTime(LocalTime.of(9, 0), "uz-UZ")).isEqualTo("to'qqiz");
        assertThat(SpokenNumbers.clockTime(LocalTime.of(14, 30), "uz-UZ")).isEqualTo("o'n to'rt o'ttiz");
        assertThat(SpokenNumbers.clockTime(LocalTime.of(9, 5), "uz-UZ")).isEqualTo("to'qqiz nol besh");
        assertThat(SpokenNumbers.clockTime(LocalTime.of(9, 0), "ru-RU")).isEqualTo("девять ноль-ноль");
        assertThat(SpokenNumbers.clockTime(LocalTime.of(18, 45), "ru-RU")).isEqualTo("восемнадцать сорок пять");
        assertThat(SpokenNumbers.clockTime(LocalTime.of(9, 5), "en-GB")).isEqualTo("9:05");
    }

    @Test
    void anythingElseIsReadInUzbek() {
        assertThat(SpokenNumbers.cardinal(5, null)).isEqualTo("besh");
        assertThat(SpokenNumbers.cardinal(5, "kk-KZ")).isEqualTo("besh");
    }
}
