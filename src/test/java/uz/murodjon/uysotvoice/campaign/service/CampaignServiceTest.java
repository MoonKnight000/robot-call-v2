package uz.murodjon.uysotvoice.campaign.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.mockito.ArgumentCaptor;

import uz.murodjon.uysotvoice.audit.service.AuditService;
import uz.murodjon.uysotvoice.campaign.dto.CampaignRow;
import uz.murodjon.uysotvoice.campaign.dto.TargetRow;
import uz.murodjon.uysotvoice.campaign.enums.CampaignStatus;
import uz.murodjon.uysotvoice.campaign.enums.CampaignType;
import uz.murodjon.uysotvoice.campaign.enums.TargetStatus;
import uz.murodjon.uysotvoice.campaign.repository.CampaignRepository;
import uz.murodjon.uysotvoice.campaign.repository.CampaignTargetRepository;
import uz.murodjon.uysotvoice.dialer.config.DialerProperties;
import uz.murodjon.uysotvoice.dialer.config.RetryProperties;
import uz.murodjon.uysotvoice.donotcall.enums.DoNotCallSource;
import uz.murodjon.uysotvoice.donotcall.repository.DoNotCallRepository;
import uz.murodjon.uysotvoice.shared.dialog.Disposition;
import uz.murodjon.uysotvoice.voice.dto.TtsVoiceRow;
import uz.murodjon.uysotvoice.voice.service.TtsVoiceService;

import java.time.Clock;
import java.time.DayOfWeek;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * The retry policy decides how many times a real person gets called back, so its
 * edges are worth pinning down: a terminal outcome must never be retried, and an
 * exhausted target must never be rescheduled.
 */
class CampaignServiceTest {

    private static final long TARGET_ID = 7L;
    private static final long CAMPAIGN_ID = 3L;

    /** Fixed so the retry arithmetic is checkable: Wednesday 2026-07-01, 10:00 local. */
    private static final Instant NOW = ZonedDateTime.of(
            LocalDate.of(2026, 7, 1), LocalTime.of(10, 0), ZoneId.systemDefault()).toInstant();

    private CampaignRepository campaigns;
    private CampaignTargetRepository targets;
    private DoNotCallRepository doNotCall;
    private TtsVoiceService voices;
    private CampaignService service;

    @BeforeEach
    void setUp() {
        campaigns = mock(CampaignRepository.class);
        targets = mock(CampaignTargetRepository.class);
        doNotCall = mock(DoNotCallRepository.class);
        voices = mock(TtsVoiceService.class);
        when(voices.find("nigora")).thenReturn(new TtsVoiceRow("nigora", "yandex", "uz-UZ", "nigora", "Nigora"));
        when(voices.ids()).thenReturn(List.of("nigora"));
        service = new CampaignService(campaigns, targets, doNotCall, voices, dialerProps(),
                mock(AuditService.class), Clock.fixed(NOW, ZoneId.systemDefault()));
    }

    private static DialerProperties dialerProps() {
        return new DialerProperties(true, 5, 3, 5, 60,
                new RetryProperties(180, 15, 1200, true));
    }

    private void givenTarget(int attempts) {
        when(targets.find(TARGET_ID)).thenReturn(new TargetRow(
                TARGET_ID, CAMPAIGN_ID, 100L, "998901112233", "uz-UZ", "{}", TargetStatus.IN_PROGRESS, attempts, false));
        when(campaigns.find(CAMPAIGN_ID)).thenReturn(campaign(3, 24));
    }

    private static CampaignRow campaign(int maxAttempts, int retryHours) {
        return new CampaignRow(CAMPAIGN_ID, "test", CampaignType.DEBT_COLLECTION, CampaignStatus.ACTIVE, "goal", "uz-UZ",
                null, null, Set.of(DayOfWeek.MONDAY, DayOfWeek.TUESDAY), maxAttempts, retryHours, 5, null, 0);
    }

    @ParameterizedTest
    @EnumSource(value = Disposition.class,
            names = {"PROMISE_TO_PAY", "REFUSED", "TRANSFERRED", "WRONG_NUMBER", "DO_NOT_CALL"})
    void terminalOutcomesCloseTheTarget(Disposition disposition) {
        givenTarget(1);

        service.applyOutcome(TARGET_ID, disposition);

        verify(targets).updateStatus(TARGET_ID, TargetStatus.DONE, null);
    }

    @Test
    void optOutIsNeverRetriedEvenOnTheFirstAttempt() {
        // §11.4: an opt-out outranks the attempt budget entirely.
        givenTarget(0);

        service.applyOutcome(TARGET_ID, Disposition.DO_NOT_CALL);

        verify(targets).updateStatus(TARGET_ID, TargetStatus.DONE, null);
        verify(targets, never()).updateStatus(eq(TARGET_ID), eq(TargetStatus.PENDING), any());
    }

    @Test
    void retryableOutcomeIsRescheduled() {
        givenTarget(1);

        service.applyOutcome(TARGET_ID, Disposition.NO_ANSWER);

        verify(targets).updateStatus(eq(TARGET_ID), eq(TargetStatus.PENDING), any(Instant.class));
    }

    @Test
    void targetIsExhaustedOnceAttemptsRunOut() {
        givenTarget(3); // maxAttempts = 3

        service.applyOutcome(TARGET_ID, Disposition.NO_ANSWER);

        verify(targets).updateStatus(TARGET_ID, TargetStatus.EXHAUSTED, null);
    }

    @Test
    void unknownTargetIsIgnored() {
        when(targets.find(TARGET_ID)).thenReturn(null);

        service.applyOutcome(TARGET_ID, Disposition.NO_ANSWER);

        verify(targets, never()).updateStatus(anyLong(), any(), any());
    }

    @Test
    void manualOptOutAlsoLandsOnThePhoneLevelList() {
        // Otherwise re-importing the number into the next campaign resumes calling.
        givenTarget(1);

        service.doNotCall(TARGET_ID);

        verify(doNotCall).add(eq("998901112233"), any(), eq(DoNotCallSource.MANUAL));
        verify(targets).setDoNotCall(TARGET_ID);
    }

    @Test
    void targetPhoneIsNormalizedAndValidatedOnImport() {
        service.addTarget(CAMPAIGN_ID, 1L, " +998 90 111-22-33 ", "uz-UZ", null);
        verify(targets).add(CAMPAIGN_ID, 1L, "+998901112233", "uz-UZ", "{}");

        assertThatThrownBy(() -> service.addTarget(CAMPAIGN_ID, 1L, "600@evil", "uz-UZ", null))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void createUsesWeekdaysWhenDialDaysOmitted() {
        service.createCampaign("c", null, null, null, null, null, null, 0, 0, 0, null, 0);

        verify(campaigns).create(eq("c"), eq(CampaignType.DEBT_COLLECTION), eq(""), eq("{}"), eq("uz-UZ"),
                any(), any(), eq(Set.of(DayOfWeek.MONDAY, DayOfWeek.TUESDAY, DayOfWeek.WEDNESDAY,
                        DayOfWeek.THURSDAY, DayOfWeek.FRIDAY)), eq(3), eq(24), eq(20),
                isNull(), eq(0));
    }

    @Test
    void createAcceptsACatalogVoiceAndRejectsAnUnknownOne() {
        // The router falls back to default routing for a voice it cannot resolve, so an
        // unchecked typo would mean a whole campaign dialled in the wrong voice.
        service.createCampaign("c", null, null, null, null, null, null, 0, 0, 0, " nigora ", 0);

        verify(campaigns).create(any(), any(), any(), any(), any(), any(), any(), any(),
                anyInt(), anyInt(), anyInt(), eq("nigora"), anyInt());

        assertThatThrownBy(() ->
                service.createCampaign("c", null, null, null, null, null, null, 0, 0, 0,
                        "nosuchvoice", 0))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("nigora");
    }

    @Test
    void negativeDailyCapIsStoredAsUnlimited() {
        service.createCampaign("c", null, null, null, null, null, null, 0, 0, 0, null, -5);

        verify(campaigns).create(any(), any(), any(), any(), any(), any(), any(), any(),
                anyInt(), anyInt(), anyInt(), isNull(), eq(0));
    }

    @Test
    void retryDelayDependsOnWhyTheCallFailed() {
        // One 24-hour interval for every outcome spent most of a campaign's three retries
        // on the case least likely to change. NO_ANSWER should come back the same day.
        when(campaigns.find(CAMPAIGN_ID)).thenReturn(new CampaignRow(CAMPAIGN_ID, "c", CampaignType.DEBT_COLLECTION,
                CampaignStatus.ACTIVE, "", "uz-UZ", null, null, Set.of(), 3, 24, 5, null, 0));
        when(targets.find(TARGET_ID)).thenReturn(new TargetRow(
                TARGET_ID, CAMPAIGN_ID, 1L, "998901112233", null, "{}", TargetStatus.IN_PROGRESS, 1, false));

        assertThat(rescheduledTo(Disposition.NO_ANSWER)).isEqualTo(NOW.plus(Duration.ofMinutes(180)));
        assertThat(rescheduledTo(Disposition.FAILED)).isEqualTo(NOW.plus(Duration.ofMinutes(15)));
        // Deliberately not a multiple of 24h: a machine answers at every hour equally.
        assertThat(rescheduledTo(Disposition.VOICEMAIL)).isEqualTo(NOW.plus(Duration.ofMinutes(1200)));
        // Anything without special handling falls back to the campaign's own interval.
        assertThat(rescheduledTo(Disposition.HUNG_UP)).isEqualTo(NOW.plus(Duration.ofHours(24)));
    }

    @Test
    void retryIsPulledIntoTheCampaignDialWindow() {
        // A raw "now + 3h" from a 10:00 Wednesday with a window closing at 11:00 would land
        // at 13:00 — outside it. §11.2 says the window is binding, so the retry moves to the
        // next allowed day's opening time rather than sitting in an illegal slot.
        when(campaigns.find(CAMPAIGN_ID)).thenReturn(new CampaignRow(CAMPAIGN_ID, "c", CampaignType.DEBT_COLLECTION,
                CampaignStatus.ACTIVE, "", "uz-UZ", LocalTime.of(9, 0), LocalTime.of(11, 0),
                Set.of(DayOfWeek.MONDAY, DayOfWeek.TUESDAY, DayOfWeek.WEDNESDAY, DayOfWeek.THURSDAY, DayOfWeek.FRIDAY),
                3, 24, 5, null, 0));
        when(targets.find(TARGET_ID)).thenReturn(new TargetRow(
                TARGET_ID, CAMPAIGN_ID, 1L, "998901112233", null, "{}", TargetStatus.IN_PROGRESS, 1, false));

        Instant next = rescheduledTo(Disposition.NO_ANSWER);

        ZonedDateTime local = next.atZone(ZoneId.systemDefault());
        assertThat(local.toLocalDate()).isEqualTo(LocalDate.of(2026, 7, 2)); // Thursday
        assertThat(local.toLocalTime()).isEqualTo(LocalTime.of(9, 0));
    }

    /** Apply {@code disposition} and return the {@code next_attempt_at} it scheduled. */
    private Instant rescheduledTo(Disposition disposition) {
        CampaignTargetRepository fresh = mock(CampaignTargetRepository.class);
        when(fresh.find(TARGET_ID)).thenReturn(targets.find(TARGET_ID));
        CampaignService scoped = new CampaignService(campaigns, fresh, doNotCall,
                voices, dialerProps(), mock(AuditService.class),
                Clock.fixed(NOW, ZoneId.systemDefault()));

        scoped.applyOutcome(TARGET_ID, disposition);

        ArgumentCaptor<Instant> at = ArgumentCaptor.forClass(Instant.class);
        verify(fresh).updateStatus(eq(TARGET_ID), eq(TargetStatus.PENDING), at.capture());
        return at.getValue();
    }

    @Test
    void nullDispositionIsTreatedAsRetryable() {
        // A call that dropped before the dialog recorded anything should be tried again.
        givenTarget(0);

        service.applyOutcome(TARGET_ID, null);

        verify(targets).updateStatus(eq(TARGET_ID), eq(TargetStatus.PENDING), any(Instant.class));
    }

    @Test
    void dialDaysAreExposedAsWeekdays() {
        assertThat(campaign(3, 24).allowedDays())
                .containsExactlyInAnyOrder(DayOfWeek.MONDAY, DayOfWeek.TUESDAY);
    }

    @Test
    void emptyDialDaysAllowEveryDay() {
        // A campaign created without one must not silently stop dialing.
        CampaignRow blank = new CampaignRow(CAMPAIGN_ID, "c", CampaignType.DEBT_COLLECTION, CampaignStatus.ACTIVE, "", "uz-UZ",
                null, null, Set.of(), 3, 24, 5, null, 0);
        assertThat(blank.allowedDays()).hasSize(7);
    }

    @Test
    void isNullSafeAboutMissingCampaign() {
        when(targets.find(TARGET_ID)).thenReturn(new TargetRow(
                TARGET_ID, CAMPAIGN_ID, 1L, "998901112233", null, "{}", TargetStatus.IN_PROGRESS, 5, false));
        when(campaigns.find(CAMPAIGN_ID)).thenReturn(null);

        service.applyOutcome(TARGET_ID, Disposition.NO_ANSWER);

        // Falls back to maxAttempts=3, and 5 attempts is already past it.
        verify(targets).updateStatus(eq(TARGET_ID), eq(TargetStatus.EXHAUSTED), isNull());
    }
}
