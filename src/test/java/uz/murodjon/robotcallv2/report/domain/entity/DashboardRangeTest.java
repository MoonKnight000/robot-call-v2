package uz.murodjon.robotcallv2.report.domain.entity;

import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.ZoneId;

import static org.assertj.core.api.Assertions.assertThat;

class DashboardRangeTest {

    private static final ZoneId TASHKENT = ZoneId.of("Asia/Tashkent");
    private static final Instant NOW = Instant.parse("2026-10-05T10:00:00Z");

    /** A bare date is the company's day: Tashkent's 5 October starts at 19:00 UTC on the 4th. */
    @Test
    void aBareDateIsTheCompanysDay() {
        DashboardRange range = DashboardRange.of("2026-10-05", "2026-10-05", TASHKENT, NOW);

        assertThat(range.from()).isEqualTo(Instant.parse("2026-10-04T19:00:00Z"));
        assertThat(range.to()).isEqualTo(Instant.parse("2026-10-05T18:59:59.999999999Z"));
    }

    @Test
    void theSevenDayPresetStartsAtTheCompanysMidnight() {
        DashboardRange range = DashboardRange.ofPreset("7d", null, null, TASHKENT, NOW);

        assertThat(range.from()).isEqualTo(Instant.parse("2026-09-28T19:00:00Z"));
        assertThat(range.to()).isEqualTo(NOW);
    }

    @Test
    void thePreviousPeriodKeepsTheZone() {
        assertThat(DashboardRange.of(null, null, TASHKENT, NOW).previous().zone()).isEqualTo(TASHKENT);
    }
}
