package uz.murodjon.robotcallv2.agent.dialog;

import org.junit.jupiter.api.Test;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * What this protects is one sentence the caller actually heard: "…2026-07-01 sanagacha
 * to'lanishi kerak bo'lgan…", read out as a run of digits. Everything here is about the
 * machine form never reaching a synthesizer — and about not mangling the figures that are
 * supposed to stay as they are.
 */
class SpokenDatesTest {

    private static final int THIS_YEAR = LocalDate.now().getYear();

    @Test
    void anIsoDateBecomesWhatAPersonWouldSay() {
        assertThat(SpokenDates.humanize(THIS_YEAR + "-07-01 sanagacha", "uz-UZ"))
                .isEqualTo("1-iyul sanagacha");
        assertThat(SpokenDates.humanize(THIS_YEAR + "-07-01", "ru-RU")).isEqualTo("1 июля");
        assertThat(SpokenDates.humanize(THIS_YEAR + "-07-01", "en-US")).isEqualTo("July 1");
    }

    @Test
    void anotherYearIsSpokenOutBecauseTheCallerNeedsIt() {
        assertThat(SpokenDates.humanize("2024-12-31", "uz-UZ")).isEqualTo("2024-yil 31-dekabr");
        assertThat(SpokenDates.humanize("2024-12-31", "ru-RU")).isEqualTo("31 декабря 2024 года");
    }

    @Test
    void everyDateInTheSentenceIsRewritten() {
        assertThat(SpokenDates.humanize(THIS_YEAR + "-01-05 dan " + THIS_YEAR + "-02-09 gacha", "uz-UZ"))
                .isEqualTo("5-yanvar dan 9-fevral gacha");
    }

    @Test
    void figuresThatAreNotDatesAreLeftAlone() {
        // A sum has to stay a sum: the prompt asks for digits there on purpose, because
        // the synthesizer reads them correctly.
        assertThat(SpokenDates.humanize("1500000 so'm", "uz-UZ")).isEqualTo("1500000 so'm");
        // A contract number shaped like a date is still a contract number.
        assertThat(SpokenDates.humanize("UY-2026-00123", "uz-UZ")).isEqualTo("UY-2026-00123");
        // ...and neither is something that only looks like one.
        assertThat(SpokenDates.humanize("2026-13-45", "uz-UZ")).isEqualTo("2026-13-45");
    }

    @Test
    void textWithoutADateComesBackUntouched() {
        assertThat(SpokenDates.humanize("Murodjon aka, sizmisiz?", "uz-UZ"))
                .isEqualTo("Murodjon aka, sizmisiz?");
        assertThat(SpokenDates.humanize(null, "uz-UZ")).isNull();
        assertThat(SpokenDates.humanize("", "uz-UZ")).isEmpty();
    }
}
