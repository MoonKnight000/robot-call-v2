package uz.murodjon.robotcallv2.dialer.domain.service;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import uz.murodjon.robotcallv2.campaign.domain.entity.Campaign;
import uz.murodjon.robotcallv2.campaign.domain.entity.CampaignSchedule;
import uz.murodjon.robotcallv2.campaign.domain.entity.CampaignRetry;
import uz.murodjon.robotcallv2.campaign.domain.entity.CampaignLimits;
import uz.murodjon.robotcallv2.campaign.domain.entity.CampaignRecurrence;
import uz.murodjon.robotcallv2.campaign.domain.enums.CampaignStatus;
import uz.murodjon.robotcallv2.campaign.domain.enums.RecurrenceType;
import uz.murodjon.robotcallv2.company.domain.entity.CompanyConfig;

import java.time.*;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class DialWindowTest {

    private final ZoneId tashkentZone = ZoneId.of("Asia/Tashkent");

    private Campaign createCampaign(LocalTime start, LocalTime end) {
        return Campaign.builder()
                .id(1L)
                .name("Test Campaign")
                .status(CampaignStatus.ACTIVE)
                .schedule(new CampaignSchedule(null, null, start, end, Set.of()))
                .retry(new CampaignRetry(3, 24, null))
                .limits(new CampaignLimits(5, 0, null, null, null))
                .aiAgentId(7L)
                .companyId(1L)
                .recurrence(new CampaignRecurrence(RecurrenceType.ONCE, null, null, false))
                .build();
    }

    @Test
    @DisplayName("RecipientTimezoneResolver maps regional and international prefixes correctly")
    void mapsRecipientTimezones() {
        assertEquals(ZoneId.of("Asia/Tashkent"), RecipientTimezoneResolver.resolve("+998901234567", tashkentZone));
        assertEquals(ZoneId.of("Asia/Almaty"), RecipientTimezoneResolver.resolve("+77011234567", tashkentZone));
        assertEquals(ZoneId.of("Europe/Moscow"), RecipientTimezoneResolver.resolve("+79031234567", tashkentZone));
        assertEquals(ZoneId.of("Europe/London"), RecipientTimezoneResolver.resolve("+447123456789", tashkentZone));
        assertEquals(ZoneId.of("America/New_York"), RecipientTimezoneResolver.resolve("+12125551234", tashkentZone));
        assertEquals(tashkentZone, RecipientTimezoneResolver.resolve(null, tashkentZone));
    }

    @Test
    @DisplayName("DialWindow isOpen respects company holiday")
    void respectsCompanyHoliday() {
        LocalDate today = LocalDate.of(2026, 10, 10);
        Instant now = today.atTime(12, 0).atZone(tashkentZone).toInstant();
        Clock clock = Clock.fixed(now, tashkentZone);

        Campaign campaign = createCampaign(LocalTime.of(9, 0), LocalTime.of(20, 0));
        CompanyConfig config = CompanyConfig.defaults();

        assertFalse(DialWindow.isOpen(campaign, config, Set.of(today), clock));
        assertTrue(DialWindow.isOpen(campaign, config, Set.of(), clock));
    }

    @Test
    @DisplayName("isOpenForRecipient blocks calls outside recipient local timezone daytime hours")
    void blocksRecipientInQuietHours() {
        // 10:00 AM Tashkent time (UTC+5) is 05:00 AM in London (UTC+0).
        // 05:00 is outside the daytime dial window (09:00 - 20:00).
        LocalDate today = LocalDate.of(2026, 10, 10);
        Instant now = today.atTime(10, 0).atZone(tashkentZone).toInstant();
        Clock clock = Clock.fixed(now, tashkentZone);

        Campaign campaign = createCampaign(LocalTime.of(9, 0), LocalTime.of(20, 0));
        CompanyConfig config = CompanyConfig.defaults();

        // Calling local Uzbekistan number at 10:00 AM Tashkent is open
        assertTrue(DialWindow.isOpenForRecipient(campaign, config, "+998901234567", clock));

        // Calling UK number (+44) when it's 5:00 AM in London is blocked by quiet hours
        assertFalse(DialWindow.isOpenForRecipient(campaign, config, "+447123456789", clock));
    }

    @Test
    @DisplayName("isOpenForRecipient allows recipient when recipient local time is within window")
    void allowsRecipientWhenWithinLocalWindow() {
        // 15:00 Tashkent time (UTC+5) is 10:00 AM in London (UTC+0).
        // 10:00 is within 09:00 - 20:00.
        LocalDate today = LocalDate.of(2026, 10, 10);
        Instant now = today.atTime(15, 0).atZone(tashkentZone).toInstant();
        Clock clock = Clock.fixed(now, tashkentZone);

        Campaign campaign = createCampaign(LocalTime.of(9, 0), LocalTime.of(20, 0));
        CompanyConfig config = CompanyConfig.defaults();

        assertTrue(DialWindow.isOpenForRecipient(campaign, config, "+447123456789", clock));
    }
}
