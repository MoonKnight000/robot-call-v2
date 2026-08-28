package uz.murodjon.uysotvoice.campaign.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.mockito.ArgumentCaptor;

import uz.murodjon.uysotvoice.audit.service.AuditService;
import uz.murodjon.uysotvoice.campaign.dto.Campaign;
import uz.murodjon.uysotvoice.campaign.dto.CampaignTarget;
import uz.murodjon.uysotvoice.campaign.dto.CreateCampaignRequest;
import uz.murodjon.uysotvoice.campaign.enums.CampaignStatus;
import uz.murodjon.uysotvoice.campaign.enums.CampaignType;
import uz.murodjon.uysotvoice.campaign.enums.TargetStatus;
import uz.murodjon.uysotvoice.campaign.repository.CampaignRepository;
import uz.murodjon.uysotvoice.campaign.repository.CampaignTargetRepository;
import uz.murodjon.uysotvoice.company.service.CompanyConfigService;
import uz.murodjon.uysotvoice.company.service.CurrentCompany;
import uz.murodjon.uysotvoice.dialer.config.DialerProperties;
import uz.murodjon.uysotvoice.dialer.config.RetryProperties;
import uz.murodjon.uysotvoice.donotcall.enums.DoNotCallSource;
import uz.murodjon.uysotvoice.donotcall.repository.DoNotCallRepository;
import uz.murodjon.uysotvoice.notification.enums.NotificationType;
import uz.murodjon.uysotvoice.notification.service.NotificationService;
import uz.murodjon.uysotvoice.scenario.service.ScenarioService;
import uz.murodjon.uysotvoice.shared.dialog.Disposition;
import uz.murodjon.uysotvoice.shared.exception.ValidationException;
import uz.murodjon.uysotvoice.user.service.UserService;
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
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
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

    private static final long SCENARIO_ID = 1L;
    private static final long COMPANY_ID = 1L;

    private CampaignRepository campaigns;
    private CampaignTargetRepository targets;
    private DoNotCallRepository doNotCall;
    private TtsVoiceService voices;
    private ScenarioService scenarios;
    private UserService users;
    private CompanyConfigService companyConfig;
    private CurrentCompany currentCompany;
    private NotificationService notifications;
    private CampaignService service;

    @BeforeEach
    void setUp() {
        campaigns = mock(CampaignRepository.class);
        targets = mock(CampaignTargetRepository.class);
        doNotCall = mock(DoNotCallRepository.class);
        voices = mock(TtsVoiceService.class);
        scenarios = mock(ScenarioService.class);
        users = mock(UserService.class);
        when(scenarios.scenarioNamesByIds(any())).thenReturn(Map.of());
        when(users.namesByIds(any())).thenReturn(Map.of());
        companyConfig = mock(CompanyConfigService.class);
        currentCompany = mock(CurrentCompany.class);
        notifications = mock(NotificationService.class);
        when(voices.isSelectable("nigora")).thenReturn(true);
        when(voices.selectableIds()).thenReturn(List.of("nigora"));
        when(currentCompany.id()).thenReturn(COMPANY_ID);
        // Mirrors CompanyConfigService.resolveLanguage's real fallback shape (null -> default,
        // otherwise pass the requested value straight through) without needing a real config row.
        when(companyConfig.resolveLanguage(anyLong(), any()))
                .thenAnswer(inv -> {
                    String requested = inv.getArgument(1);
                    return requested != null && !requested.isBlank() ? requested : "uz-UZ";
                });
        service = new CampaignService(campaigns, targets, doNotCall, voices, scenarios, users, companyConfig,
                currentCompany, dialerProps(), mock(AuditService.class), notifications,
                Clock.fixed(NOW, ZoneId.systemDefault()));
    }

    private static DialerProperties dialerProps() {
        return new DialerProperties(true, 5, 3, 5, 60,
                new RetryProperties(180, 15, 1200, true));
    }

    private void givenTarget(int attempts) {
        when(targets.find(TARGET_ID)).thenReturn(new CampaignTarget(
                TARGET_ID, CAMPAIGN_ID, 100L, "998901112233", "uz-UZ", "{}", TargetStatus.IN_PROGRESS, attempts, false));
        when(campaigns.find(CAMPAIGN_ID)).thenReturn(campaign(3, 0));
    }

    private static Campaign campaign(int maxAttempts, int retryMinutes) {
        return new Campaign(CAMPAIGN_ID, "test", CampaignType.DEBT_COLLECTION, CampaignStatus.ACTIVE, "goal", "uz-UZ",
                null, null, Set.of(DayOfWeek.MONDAY, DayOfWeek.TUESDAY), maxAttempts, retryMinutes, 5, null, 0,
                SCENARIO_ID, COMPANY_ID, true, null);
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
    void campaignCompletesAndNotifiesWhenLastActiveTargetFinishes() {
        givenTarget(1);
        when(targets.countActive(CAMPAIGN_ID)).thenReturn(0L);

        service.applyOutcome(TARGET_ID, Disposition.PROMISE_TO_PAY);

        verify(campaigns).updateStatus(CAMPAIGN_ID, CampaignStatus.COMPLETED);
        verify(notifications).notify(eq(COMPANY_ID), eq(NotificationType.CAMPAIGN_FINISHED), any(), any(), isNull());
    }

    @Test
    void campaignStaysActiveWhileTargetsAreStillInTheDialLoop() {
        givenTarget(1);
        when(targets.countActive(CAMPAIGN_ID)).thenReturn(2L);

        service.applyOutcome(TARGET_ID, Disposition.PROMISE_TO_PAY);

        verify(campaigns, never()).updateStatus(eq(CAMPAIGN_ID), eq(CampaignStatus.COMPLETED));
        verify(notifications, never()).notify(anyLong(), eq(NotificationType.CAMPAIGN_FINISHED), any(), any(), any());
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
                .isInstanceOf(ValidationException.class);
    }

    private static CreateCampaignRequest createRequest(String ttsVoice, int dailyCallCap) {
        return new CreateCampaignRequest("c", CampaignType.DEBT_COLLECTION, null, null, null, null, null,
                0, 0, 0, ttsVoice, dailyCallCap, SCENARIO_ID, null);
    }

    @Test
    void createUsesWeekdaysWhenDialDaysOmitted() {
        service.createCampaign(createRequest(null, 0));

        ArgumentCaptor<Campaign> row = ArgumentCaptor.forClass(Campaign.class);
        verify(campaigns).create(row.capture());
        assertThat(row.getValue().name()).isEqualTo("c");
        assertThat(row.getValue().type()).isEqualTo(CampaignType.DEBT_COLLECTION);
        assertThat(row.getValue().goalPrompt()).isEqualTo("");
        assertThat(row.getValue().defaultLanguage()).isEqualTo("uz-UZ");
        assertThat(row.getValue().dialDays()).isEqualTo(Set.of(DayOfWeek.MONDAY, DayOfWeek.TUESDAY,
                DayOfWeek.WEDNESDAY, DayOfWeek.THURSDAY, DayOfWeek.FRIDAY));
        assertThat(row.getValue().maxAttempts()).isEqualTo(3);
        // 0 is kept as sent: it means "no campaign preference", and the retry then
        // follows the per-disposition defaults (RetrySchedule).
        assertThat(row.getValue().retryIntervalMinutes()).isZero();
        assertThat(row.getValue().maxConcurrentCalls()).isEqualTo(20);
        assertThat(row.getValue().ttsVoice()).isNull();
        assertThat(row.getValue().scenarioId()).isEqualTo(SCENARIO_ID);
    }

    @Test
    void createAcceptsACatalogVoiceAndRejectsAnUnknownOne() {
        // The router falls back to default routing for a voice it cannot resolve, so an
        // unchecked typo would mean a whole campaign dialled in the wrong voice.
        service.createCampaign(createRequest(" nigora ", 0));

        ArgumentCaptor<Campaign> row = ArgumentCaptor.forClass(Campaign.class);
        verify(campaigns).create(row.capture());
        assertThat(row.getValue().ttsVoice()).isEqualTo("nigora");

        assertThatThrownBy(() -> service.createCampaign(createRequest("nosuchvoice", 0)))
                .isInstanceOf(ValidationException.class)
                .hasMessageContaining("nigora");
    }

    @Test
    void negativeDailyCapIsStoredAsUnlimited() {
        service.createCampaign(createRequest(null, -5));

        ArgumentCaptor<Campaign> row = ArgumentCaptor.forClass(Campaign.class);
        verify(campaigns).create(row.capture());
        assertThat(row.getValue().dailyCallCap()).isEqualTo(0);
    }

    @Test
    void retryDelayDependsOnWhyTheCallFailed() {
        // One 24-hour interval for every outcome spent most of a campaign's three retries
        // on the case least likely to change. NO_ANSWER should come back the same day.
        when(campaigns.find(CAMPAIGN_ID)).thenReturn(new Campaign(CAMPAIGN_ID, "c", CampaignType.DEBT_COLLECTION,
                CampaignStatus.ACTIVE, "", "uz-UZ", null, null, Set.of(), 3, 0, 5, null, 0, SCENARIO_ID, COMPANY_ID, true, null));
        when(targets.find(TARGET_ID)).thenReturn(new CampaignTarget(
                TARGET_ID, CAMPAIGN_ID, 1L, "998901112233", null, "{}", TargetStatus.IN_PROGRESS, 1, false));

        assertThat(rescheduledTo(Disposition.NO_ANSWER)).isEqualTo(NOW.plus(Duration.ofMinutes(180)));
        assertThat(rescheduledTo(Disposition.FAILED)).isEqualTo(NOW.plus(Duration.ofMinutes(15)));
        // Deliberately not a multiple of 24h: a machine answers at every hour equally.
        assertThat(rescheduledTo(Disposition.VOICEMAIL)).isEqualTo(NOW.plus(Duration.ofMinutes(1200)));
        // Anything without special handling falls back to the default interval (24h).
        assertThat(rescheduledTo(Disposition.HUNG_UP)).isEqualTo(NOW.plus(Duration.ofHours(24)));
    }

    @Test
    void retryIsPulledIntoTheCampaignDialWindow() {
        // A raw "now + 3h" from a 10:00 Wednesday with a window closing at 11:00 would land
        // at 13:00 — outside it. §11.2 says the window is binding, so the retry moves to the
        // next allowed day's opening time rather than sitting in an illegal slot.
        when(campaigns.find(CAMPAIGN_ID)).thenReturn(new Campaign(CAMPAIGN_ID, "c", CampaignType.DEBT_COLLECTION,
                CampaignStatus.ACTIVE, "", "uz-UZ", LocalTime.of(9, 0), LocalTime.of(11, 0),
                Set.of(DayOfWeek.MONDAY, DayOfWeek.TUESDAY, DayOfWeek.WEDNESDAY, DayOfWeek.THURSDAY, DayOfWeek.FRIDAY),
                3, 0, 5, null, 0, SCENARIO_ID, COMPANY_ID, true, null));
        when(targets.find(TARGET_ID)).thenReturn(new CampaignTarget(
                TARGET_ID, CAMPAIGN_ID, 1L, "998901112233", null, "{}", TargetStatus.IN_PROGRESS, 1, false));

        Instant next = rescheduledTo(Disposition.NO_ANSWER);

        ZonedDateTime local = next.atZone(ZoneId.systemDefault());
        assertThat(local.toLocalDate()).isEqualTo(LocalDate.of(2026, 7, 2)); // Thursday
        assertThat(local.toLocalTime()).isEqualTo(LocalTime.of(9, 0));
    }

    /** Apply {@code disposition} and return the {@code next_attempt_at} it scheduled. */
    private Instant rescheduledTo(Disposition disposition) {
        CampaignTarget target = targets.find(TARGET_ID);
        CampaignTargetRepository fresh = mock(CampaignTargetRepository.class);
        when(fresh.find(TARGET_ID)).thenReturn(target);
        CampaignService scoped = new CampaignService(campaigns, fresh, doNotCall,
                voices, scenarios, users, companyConfig, currentCompany, dialerProps(), mock(AuditService.class),
                mock(NotificationService.class), Clock.fixed(NOW, ZoneId.systemDefault()));

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
        assertThat(campaign(3, 0).allowedDays())
                .containsExactlyInAnyOrder(DayOfWeek.MONDAY, DayOfWeek.TUESDAY);
    }

    @Test
    void emptyDialDaysAllowEveryDay() {
        // A campaign created without one must not silently stop dialing.
        Campaign blank = new Campaign(CAMPAIGN_ID, "c", CampaignType.DEBT_COLLECTION, CampaignStatus.ACTIVE, "", "uz-UZ",
                null, null, Set.of(), 3, 24, 5, null, 0, SCENARIO_ID, COMPANY_ID, true, null);
        assertThat(blank.allowedDays()).hasSize(7);
    }

    @Test
    void isNullSafeAboutMissingCampaign() {
        when(targets.find(TARGET_ID)).thenReturn(new CampaignTarget(
                TARGET_ID, CAMPAIGN_ID, 1L, "998901112233", null, "{}", TargetStatus.IN_PROGRESS, 5, false));
        when(campaigns.find(CAMPAIGN_ID)).thenReturn(null);

        service.applyOutcome(TARGET_ID, Disposition.NO_ANSWER);

        // Falls back to maxAttempts=3, and 5 attempts is already past it.
        verify(targets).updateStatus(eq(TARGET_ID), eq(TargetStatus.EXHAUSTED), isNull());
    }
}
