package uz.murodjon.robotcallv2.campaign.application.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import uz.murodjon.robotcallv2.agent.tts.TtsWarmup;
import uz.murodjon.robotcallv2.aiagent.AiAgentFixtures;
import uz.murodjon.robotcallv2.aiagent.application.port.input.AiAgentUseCase;
import uz.murodjon.robotcallv2.audit.application.port.input.AuditUseCase;
import uz.murodjon.robotcallv2.campaign.application.dto.CampaignDialingDto;
import uz.murodjon.robotcallv2.campaign.application.dto.CampaignLimitsDto;
import uz.murodjon.robotcallv2.campaign.application.dto.CampaignRecurrenceDto;
import uz.murodjon.robotcallv2.campaign.application.dto.CampaignRetryDto;
import uz.murodjon.robotcallv2.campaign.application.dto.CampaignScheduleDto;
import uz.murodjon.robotcallv2.campaign.application.dto.CreateCampaignRequest;
import uz.murodjon.robotcallv2.campaign.application.dto.TargetImportResult;
import uz.murodjon.robotcallv2.campaign.application.dto.UpdateCampaignRequest;
import uz.murodjon.robotcallv2.campaign.application.port.output.*;
import uz.murodjon.robotcallv2.campaign.domain.entity.Campaign;
import uz.murodjon.robotcallv2.campaign.domain.entity.CampaignFilter;
import uz.murodjon.robotcallv2.campaign.domain.entity.CampaignLimits;
import uz.murodjon.robotcallv2.campaign.domain.entity.CampaignRecurrence;
import uz.murodjon.robotcallv2.campaign.domain.entity.CampaignRetry;
import uz.murodjon.robotcallv2.campaign.domain.entity.CampaignSchedule;
import uz.murodjon.robotcallv2.campaign.domain.entity.CampaignTarget;
import uz.murodjon.robotcallv2.campaign.domain.entity.CampaignTargetStats;
import uz.murodjon.robotcallv2.campaign.domain.enums.*;
import uz.murodjon.robotcallv2.campaign.domain.service.CampaignValidator;
import uz.murodjon.robotcallv2.campaign.infrastructure.config.RetryProperties;
import uz.murodjon.robotcallv2.company.application.port.input.CompanyConfigUseCase;
import uz.murodjon.robotcallv2.company.domain.entity.CompanyConfig;
import uz.murodjon.robotcallv2.donotcall.application.port.input.DoNotCallUseCase;
import uz.murodjon.robotcallv2.notification.application.port.input.NotificationUseCase;
import uz.murodjon.robotcallv2.shared.dialog.Disposition;
import uz.murodjon.robotcallv2.shared.exception.ConflictException;
import uz.murodjon.robotcallv2.shared.exception.NotFoundException;
import uz.murodjon.robotcallv2.shared.util.SecretCipher;
import uz.murodjon.robotcallv2.sms.application.port.input.SmsUseCase;
import uz.murodjon.robotcallv2.user.application.port.input.UserUseCase;

import java.time.Clock;
import java.time.DayOfWeek;
import java.time.LocalTime;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.*;

class CampaignServiceTest {

    private static final long CAMPAIGN_ID = 3L;
    private static final long AI_AGENT_ID = 7L;
    private static final long TARGET_ID = 42L;

    private CampaignRepository campaigns;
    private CampaignTargetRepository targets;
    private DoNotCallUseCase doNotCallUseCase;
    private AiAgentUseCase aiAgents;
    private UserUseCase users;
    private CompanyConfigUseCase companyConfig;
    private RetryProperties retryProperties;
    private AuditUseCase audit;
    private NotificationUseCase notifications;
    private TtsWarmup ttsWarmup;
    private TargetSourceRepository targetSources;
    private CallQueueLookupPort callQueues;
    private Clock clock;
    private CampaignService service;
    private CampaignTargetService targetService;

    @BeforeEach
    void setUp() {
        campaigns = mock(CampaignRepository.class);
        targets = mock(CampaignTargetRepository.class);
        doNotCallUseCase = mock(DoNotCallUseCase.class);
        aiAgents = mock(AiAgentUseCase.class);
        targetSources = mock(TargetSourceRepository.class);
        callQueues = mock(CallQueueLookupPort.class);
        users = mock(UserUseCase.class);
        companyConfig = mock(CompanyConfigUseCase.class);
        retryProperties = new RetryProperties(180, 15, 1200, true);
        audit = mock(AuditUseCase.class);
        notifications = mock(NotificationUseCase.class);
        ttsWarmup = mock(TtsWarmup.class);
        clock = Clock.systemDefaultZone();

        CampaignOutcomeService outcomeService = new CampaignOutcomeService(campaigns, targets, companyConfig,
                retryProperties, audit, notifications, mock(SmsUseCase.class), new ObjectMapper(), clock);
        targetService = new CampaignTargetService(campaigns, targets, targetSources, doNotCallUseCase, audit,
                notifications, mock(TargetApiImporter.class), mock(ChainedTargetImporter.class),
                mock(DebtorSourcePort.class), new CampaignValidator(), mock(SecretCipher.class), outcomeService, clock, new ObjectMapper());
        service = new CampaignService(campaigns, targets, targetSources, aiAgents, users, companyConfig, audit,
                notifications, ttsWarmup, targetService, outcomeService, new CampaignValidator(), callQueues, clock);
    }

    private Campaign campaign(int maxAttempts, int retryIntervalMinutes) {
        return Campaign.builder()
                .id(CAMPAIGN_ID)
                .name("c")
                .type(CampaignType.DEBT_COLLECTION)
                .status(CampaignStatus.ACTIVE)
                .schedule(new CampaignSchedule(null, null, LocalTime.of(9, 0), LocalTime.of(20, 0),
                        Set.of(DayOfWeek.MONDAY)))
                .retry(new CampaignRetry(maxAttempts, retryIntervalMinutes, null))
                .limits(new CampaignLimits(5, 0, null, null))
                .aiAgentId(AI_AGENT_ID)
                .companyId(1L)
                .recurrence(new CampaignRecurrence(RecurrenceType.ONCE, null, null, false))
                .build();
    }

    @Test
    void filterCampaignsDelegatesToRepositoryWithConsistentPagingAndEnrichesTargetStats() {
        Campaign c = campaign(3, 0);
        CampaignFilter filter = new CampaignFilter(0, 10, null, "test", CampaignStatus.ACTIVE,
                CampaignType.DEBT_COLLECTION, 1L, null, null, null, null);
        when(campaigns.findAll(1L, filter)).thenReturn(List.of(c));
        when(campaigns.countByFilter(1L, filter)).thenReturn(1L);
        when(targets.findStatsByCampaignIds(1L, Set.of(CAMPAIGN_ID))).thenReturn(
                Map.of(CAMPAIGN_ID, new CampaignTargetStats(50, 20, 30, 15)));

        var result = service.filterCampaigns(1L, filter);

        assertThat(result.data()).hasSize(1);
        var row = result.data().getFirst();
        assertThat(row.id()).isEqualTo(CAMPAIGN_ID);
        assertThat(row.stats().totalTargets()).isEqualTo(50L);
        assertThat(row.stats().calledTargets()).isEqualTo(20L);
        assertThat(row.stats().pendingTargets()).isEqualTo(30L);
        assertThat(row.stats().completedTargets()).isEqualTo(15L);
        assertThat(result.totalElements()).isEqualTo(1L);
        assertThat(result.currentPage()).isZero();
    }

    @Test
    void requireCampaignRowEnrichesTargetStats() {
        Campaign c = campaign(3, 0);
        when(campaigns.find(1L, CAMPAIGN_ID)).thenReturn(c);
        when(targets.findStatsByCampaignId(1L, CAMPAIGN_ID)).thenReturn(
                new CampaignTargetStats(100, 45, 55, 30));

        var row = service.requireCampaignRow(1L, CAMPAIGN_ID);

        assertThat(row.id()).isEqualTo(CAMPAIGN_ID);
        assertThat(row.stats().totalTargets()).isEqualTo(100L);
        assertThat(row.stats().calledTargets()).isEqualTo(45L);
        assertThat(row.stats().pendingTargets()).isEqualTo(55L);
        assertThat(row.stats().completedTargets()).isEqualTo(30L);
    }

    @Test
    void isNullSafeAboutMissingCampaign() {
        when(targets.find(1L, TARGET_ID)).thenReturn(new CampaignTarget(
                TARGET_ID, CAMPAIGN_ID, 1L, "998901112233", null, "{}", TargetStatus.IN_PROGRESS, 5, false));
        when(campaigns.find(1L, CAMPAIGN_ID)).thenReturn(null);

        targetService.applyOutcome(1L, TARGET_ID, Disposition.NO_ANSWER);

        // Falls back to maxAttempts=3, and 5 attempts is already past it.
        verify(targets).updateStatus(eq(TARGET_ID), eq(TargetStatus.EXHAUSTED), isNull());
    }

    /** One do-not-call lookup and one batched insert for the whole file; the counters stay what they were per row. */
    @Test
    @SuppressWarnings("unchecked")
    void importTargetsCsvLooksUpDoNotCallOnceAndInsertsInOneBatch() {
        when(campaigns.find(1L, CAMPAIGN_ID)).thenReturn(campaign(3, 0));
        when(targets.findQueuedPhones(1L, CAMPAIGN_ID)).thenReturn(Set.of("+998901110002"));
        when(doNotCallUseCase.findBlockedPhones(1L, List.of("+998901110001", "+998901110003", "+998901110004")))
                .thenReturn(Set.of("+998901110003"));
        // +998901110004 was queued by someone else after the queued numbers were read.
        when(targets.insertAllQueued(eq(1L), anyList()))
                .thenReturn(Map.of("+998901110001", 100L, "+998901110003", 101L));
        String csv = "phone\n+998901110001\n+998901110002\n+998901110001\n+998901110003\n+998901110004\n";

        TargetImportResult result = targetService.importTargetsCsv(1L, CAMPAIGN_ID, csv);

        assertThat(result.added()).isEqualTo(2);
        assertThat(result.targetIds()).containsExactly(100L, 101L);
        assertThat(result.skippedDuplicates()).isEqualTo(3);
        assertThat(result.skippedDoNotCall()).isEqualTo(1);
        verify(doNotCallUseCase, never()).contains(anyLong(), anyString());
        ArgumentCaptor<List<CampaignTarget>> rows = ArgumentCaptor.forClass(List.class);
        verify(targets, times(1)).insertAllQueued(eq(1L), rows.capture());
        assertThat(rows.getValue()).extracting(CampaignTarget::phone, CampaignTarget::doNotCall).containsExactly(
                tuple("+998901110001", false), tuple("+998901110003", true), tuple("+998901110004", false));
    }

    /** The rows are written by campaign id alone, so another company's campaign must not take them. */
    @Test
    void importTargetsCsvIntoACampaignTheCompanyDoesNotHaveIsNotFound() {
        when(campaigns.find(1L, CAMPAIGN_ID)).thenReturn(null);

        assertThatThrownBy(() -> targetService.importTargetsCsv(1L, CAMPAIGN_ID, "phone\n+998901110001\n"))
                .isInstanceOf(NotFoundException.class);
        verify(targets, never()).insertAllQueued(anyLong(), anyList());
    }

    @Test
    void updateCampaignAnswers409WhenTheCampaignWasSavedAfterItWasRead() {
        when(campaigns.find(1L, CAMPAIGN_ID)).thenReturn(campaign(3, 0));
        when(aiAgents.requireAgent(1L, AI_AGENT_ID)).thenReturn(AiAgentFixtures.agent(AI_AGENT_ID, 1L, 0L, "uz-UZ", null));
        when(campaigns.update(eq(1L), eq(CAMPAIGN_ID), any())).thenReturn(false);

        assertThatThrownBy(() -> service.updateCampaign(1L, CAMPAIGN_ID, updateRequest(5L)))
                .isInstanceOf(ConflictException.class);
        ArgumentCaptor<Campaign> written = ArgumentCaptor.forClass(Campaign.class);
        verify(campaigns).update(eq(1L), eq(CAMPAIGN_ID), written.capture());
        assertThat(written.getValue().version()).isEqualTo(5L);
    }

    /** A client that predates the version field edits against the version just read. */
    @Test
    void updateCampaignWithoutVersionUsesTheOneJustRead() {
        when(campaigns.find(1L, CAMPAIGN_ID)).thenReturn(campaign(3, 0));
        when(aiAgents.requireAgent(1L, AI_AGENT_ID)).thenReturn(AiAgentFixtures.agent(AI_AGENT_ID, 1L, 0L, "uz-UZ", null));
        when(campaigns.update(eq(1L), eq(CAMPAIGN_ID), any())).thenReturn(true);

        service.updateCampaign(1L, CAMPAIGN_ID, updateRequest(null));

        ArgumentCaptor<Campaign> written = ArgumentCaptor.forClass(Campaign.class);
        verify(campaigns).update(eq(1L), eq(CAMPAIGN_ID), written.capture());
        assertThat(written.getValue().version()).isZero();
    }

    @Test
    void previewCampaignIsCreatedWithItsQueueAndNoAgent() {
        when(callQueues.existsByCompanyIdAndId(1L, 5L)).thenReturn(true);

        service.createCampaign(1L, createRequest(DialMode.PREVIEW, null, 5L));

        ArgumentCaptor<Campaign> written = ArgumentCaptor.forClass(Campaign.class);
        verify(campaigns).create(eq(1L), written.capture());
        assertThat(written.getValue().dialing().mode()).isEqualTo(DialMode.PREVIEW);
        assertThat(written.getValue().dialing().callQueueId()).isEqualTo(5L);
        assertThat(written.getValue().aiAgentId()).isNull();
        verifyNoInteractions(aiAgents);
    }

    /** A queue id from another tenant would hand this company's clients to its operators. */
    @Test
    void campaignNamingAnotherCompanysQueueIsNotFound() {
        when(callQueues.existsByCompanyIdAndId(1L, 5L)).thenReturn(false);

        assertThatThrownBy(() -> service.createCampaign(1L, createRequest(DialMode.PREVIEW, null, 5L)))
                .isInstanceOf(NotFoundException.class);
        verify(campaigns, never()).create(anyLong(), any());
    }

    @Test
    void campaignWithoutDialModeIsAnAiCampaign() {
        when(aiAgents.requireAgent(1L, AI_AGENT_ID)).thenReturn(AiAgentFixtures.agent(AI_AGENT_ID, 1L, 0L, "uz-UZ", null));

        service.createCampaign(1L, createRequest(null, AI_AGENT_ID, null));

        ArgumentCaptor<Campaign> written = ArgumentCaptor.forClass(Campaign.class);
        verify(campaigns).create(eq(1L), written.capture());
        assertThat(written.getValue().dialing().mode()).isEqualTo(DialMode.AI);
        assertThat(written.getValue().aiAgentId()).isEqualTo(AI_AGENT_ID);
    }

    /** A campaign that names no hours calls in the company's own dial window. */
    @Test
    void campaignWithoutWindowTakesTheCompanysDialWindow() {
        when(aiAgents.requireAgent(1L, AI_AGENT_ID)).thenReturn(AiAgentFixtures.agent(AI_AGENT_ID, 1L, 0L, "uz-UZ", null));
        when(companyConfig.find(1L)).thenReturn(CompanyConfig.settings(LocalTime.of(10, 0), LocalTime.of(18, 0),
                "Asia/Tashkent", 0, 0, false, false));

        service.createCampaign(1L, createRequestWithWindow(null, null));

        ArgumentCaptor<Campaign> written = ArgumentCaptor.forClass(Campaign.class);
        verify(campaigns).create(eq(1L), written.capture());
        assertThat(written.getValue().schedule().dialWindowStart()).isEqualTo(LocalTime.of(10, 0));
        assertThat(written.getValue().schedule().dialWindowEnd()).isEqualTo(LocalTime.of(18, 0));
    }

    @Test
    void campaignWithoutWindowOrCompanySettingsCallsNineToEight() {
        when(aiAgents.requireAgent(1L, AI_AGENT_ID)).thenReturn(AiAgentFixtures.agent(AI_AGENT_ID, 1L, 0L, "uz-UZ", null));

        service.createCampaign(1L, createRequestWithWindow(null, null));

        ArgumentCaptor<Campaign> written = ArgumentCaptor.forClass(Campaign.class);
        verify(campaigns).create(eq(1L), written.capture());
        assertThat(written.getValue().schedule().dialWindowStart()).isEqualTo(LocalTime.of(9, 0));
        assertThat(written.getValue().schedule().dialWindowEnd()).isEqualTo(LocalTime.of(20, 0));
    }

    @Test
    void updateCampaignWithoutWindowKeepsItsOwn() {
        when(campaigns.find(1L, CAMPAIGN_ID)).thenReturn(campaign(3, 0));
        when(aiAgents.requireAgent(1L, AI_AGENT_ID)).thenReturn(AiAgentFixtures.agent(AI_AGENT_ID, 1L, 0L, "uz-UZ", null));
        when(campaigns.update(eq(1L), eq(CAMPAIGN_ID), any())).thenReturn(true);

        service.updateCampaign(1L, CAMPAIGN_ID, new UpdateCampaignRequest("c", AI_AGENT_ID, null,
                schedule(null, null), retry(), limits(), recurrence(), null, null));

        ArgumentCaptor<Campaign> written = ArgumentCaptor.forClass(Campaign.class);
        verify(campaigns).update(eq(1L), eq(CAMPAIGN_ID), written.capture());
        assertThat(written.getValue().schedule().dialWindowStart()).isEqualTo(LocalTime.of(9, 0));
        assertThat(written.getValue().schedule().dialWindowEnd()).isEqualTo(LocalTime.of(20, 0));
    }

    private static CreateCampaignRequest createRequestWithWindow(LocalTime start, LocalTime end) {
        return new CreateCampaignRequest("c", CampaignType.SURVEY, AI_AGENT_ID, null,
                schedule(start, end), retry(), limits(), recurrence(), null);
    }

    private static CreateCampaignRequest createRequest(DialMode dialMode, Long aiAgentId, Long callQueueId) {
        return new CreateCampaignRequest("c", CampaignType.SURVEY, aiAgentId, null,
                schedule(LocalTime.of(9, 0), LocalTime.of(20, 0)), retry(), limits(), recurrence(),
                new CampaignDialingDto(dialMode, callQueueId, null, null));
    }

    private static UpdateCampaignRequest updateRequest(Long version) {
        return new UpdateCampaignRequest("c", AI_AGENT_ID, null,
                schedule(LocalTime.of(9, 0), LocalTime.of(20, 0)), retry(), limits(), recurrence(), null, version);
    }

    private static CampaignScheduleDto schedule(LocalTime start, LocalTime end) {
        return new CampaignScheduleDto(null, null, start, end, Set.of(DayOfWeek.MONDAY));
    }

    private static CampaignRetryDto retry() {
        return new CampaignRetryDto(3, 0, null);
    }

    private static CampaignLimitsDto limits() {
        return new CampaignLimitsDto(5, 0, null, null);
    }

    private static CampaignRecurrenceDto recurrence() {
        return new CampaignRecurrenceDto(RecurrenceType.ONCE, null, null, false);
    }
}
