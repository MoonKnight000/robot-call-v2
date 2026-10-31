package uz.murodjon.robotcallv2.dialer.application.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.amqp.ImmediateRequeueAmqpException;
import uz.murodjon.robotcallv2.agent.ari.AriService;
import uz.murodjon.robotcallv2.agent.rtp.RtpPortAllocator;
import uz.murodjon.robotcallv2.aiagent.AiAgentFixtures;
import uz.murodjon.robotcallv2.aiagent.application.port.input.AiAgentUseCase;
import uz.murodjon.robotcallv2.audit.application.service.AuditService;
import uz.murodjon.robotcallv2.billing.application.port.input.CallBillingUseCase;
import uz.murodjon.robotcallv2.callrecord.application.port.input.CallAttemptUseCase;
import uz.murodjon.robotcallv2.campaign.application.port.input.CampaignTargetUseCase;
import uz.murodjon.robotcallv2.campaign.application.port.input.CampaignUseCase;
import uz.murodjon.robotcallv2.campaign.domain.entity.Campaign;
import uz.murodjon.robotcallv2.campaign.domain.entity.CampaignSchedule;
import uz.murodjon.robotcallv2.campaign.domain.entity.CampaignRetry;
import uz.murodjon.robotcallv2.campaign.domain.entity.CampaignLimits;
import uz.murodjon.robotcallv2.campaign.domain.entity.CampaignRecurrence;
import uz.murodjon.robotcallv2.campaign.domain.enums.CampaignStatus;
import uz.murodjon.robotcallv2.campaign.domain.enums.RecurrenceType;
import uz.murodjon.robotcallv2.company.CompanyFixtures;
import uz.murodjon.robotcallv2.crm.application.port.input.CrmUseCase;
import uz.murodjon.robotcallv2.crm.domain.entity.CrmClientSnapshot;
import uz.murodjon.robotcallv2.dialer.application.dto.CallTask;
import uz.murodjon.robotcallv2.dialer.application.dto.OutboundCall;
import uz.murodjon.robotcallv2.donotcall.application.port.input.DoNotCallUseCase;
import uz.murodjon.robotcallv2.memory.application.port.input.ClientMemoryUseCase;
import uz.murodjon.robotcallv2.scenario.application.port.input.FactWebhookUseCase;
import uz.murodjon.robotcallv2.scenario.domain.entity.Scenario;
import uz.murodjon.robotcallv2.scenario.domain.entity.ScenarioDefinition;
import uz.murodjon.robotcallv2.shared.dialog.Disposition;
import uz.murodjon.robotcallv2.shared.exception.ConflictException;
import uz.murodjon.robotcallv2.shared.exception.ErrorCode;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class CallTaskConsumerTest {

    @Mock
    private AriService ariService;
    @Mock
    private OutboundCallRegistry callRegistry;
    @Mock
    private CampaignUseCase campaignUseCase;
    @Mock
    private CampaignTargetUseCase campaignTargetUseCase;
    @Mock
    private CrmUseCase crmClient;
    @Mock
    private DoNotCallUseCase doNotCallUseCase;
    @Mock
    private AuditService audit;
    @Mock
    private DialerState dialerState;
    @Mock
    private CallAttemptUseCase callRecordService;
    @Mock
    private ClientMemoryUseCase clientMemoryService;
    @Mock
    private AiAgentUseCase aiAgents;
    @Mock
    private DialerService dialerService;
    @Mock
    private RtpPortAllocator portAllocator;

    private CallTaskConsumer consumer;

    @BeforeEach
    void setUp() {
        consumer = new CallTaskConsumer(
                ariService, callRegistry, campaignUseCase, campaignTargetUseCase,
                audit, dialerState, doNotCallUseCase, callRecordService,
                new OutboundCallPreparer(crmClient, clientMemoryService, mock(FactWebhookUseCase.class), aiAgents,
                        new ObjectMapper()),
                mock(CallBillingUseCase.class), dialerService, CompanyFixtures.openCompanyUseCase(), portAllocator,
                mock(CampaignCallPacer.class)
        );
    }

    /** Ports all taken here, ringing calls included: the task goes back for another instance, undialled and unreleased. */
    @Test
    void aFullInstanceReturnsTheTaskToTheQueue() {
        CallTask task = new CallTask(1L, 100L, 200L, "998901234567", "uz", "{}", 1L, 7L,
                null, null, null, null, null);
        when(portAllocator.available()).thenReturn(2);
        when(callRegistry.countUnanswered()).thenReturn(2);

        assertThrows(ImmediateRequeueAmqpException.class, () -> consumer.onMessage(task));

        verify(ariService, never()).originate(anyString(), anyLong(), any(OutboundCall.class));
        verify(dialerState, never()).release(anyLong(), anyLong());
    }

    /** Paused while the task waited in the queue: nothing is dialled and the claim goes back, attempt and all. */
    @Test
    void aTaskForAPausedCampaignIsGivenBackWithoutDialling() {
        CallTask task = new CallTask(1L, 100L, 200L, "998901234567", "uz", "{}", 1L, 7L,
                null, null, null, null, null);
        when(campaignUseCase.findCampaign(1L, 1L)).thenReturn(Campaign.builder()
                .id(1L)
                .name("c")
                .status(CampaignStatus.PAUSED)
                .schedule(new CampaignSchedule(null, null, LocalTime.of(9, 0), LocalTime.of(20, 0), Set.of()))
                .retry(new CampaignRetry(3, 24, null))
                .limits(new CampaignLimits(5, 0, null, null, null))
                .aiAgentId(7L)
                .companyId(1L)
                .recurrence(new CampaignRecurrence(RecurrenceType.ONCE, null, null, false))
                .build());

        consumer.process(task, dialerState);

        verify(dialerState).release(1L, 1L);
        verify(campaignTargetUseCase).releaseClaim(eq(100L), any(Instant.class));
        verify(campaignTargetUseCase, never()).applyOutcome(anyLong(), anyLong(), any());
        verify(ariService, never()).originate(anyString(), anyLong(), any(OutboundCall.class));
    }

    @Test
    void dropsBlockedNumberAndReleasesDialerState() {
        CallTask task = new CallTask(1L, 100L, 200L, "998901234567", "uz", "{}", 1L, 7L,
                null, null, null, null, null);

        when(doNotCallUseCase.contains(1L, "998901234567")).thenReturn(true);

        consumer.process(task, dialerState);

        verify(dialerState).release(1L, 1L);
        verify(campaignTargetUseCase).applyOutcome(1L, 100L, Disposition.DO_NOT_CALL);
        verify(ariService, never()).originate(anyString(), anyLong(), any(OutboundCall.class));
    }

    @Test
    void recordsDoNotCallWhenFinalOriginationGuardFindsNewOptOut() {
        CallTask task = new CallTask(1L, 100L, 200L, "998901234567", "uz", "{}", 1L, 7L,
                null, null, null, null, null);
        ScenarioDefinition definition = new ScenarioDefinition(List.of(), List.of(), List.of(), List.of(),
                "system prompt", List.of(), "disclosure");
        Scenario scenario = new Scenario(10L, "test-scenario", 1, "Test", "Desc", false, true,
                definition, Instant.now(), 1L);
        when(aiAgents.requireCallAgent(1L, 7L, "998901234567"))
                .thenReturn(AiAgentFixtures.agent(7L, 1L, 10L, "uz", "dilfuza"));
        when(aiAgents.resolveScenario(eq(1L), any())).thenReturn(scenario);
        when(crmClient.fetchClient(1L, 200L)).thenReturn(new CrmClientSnapshot("Ali",
                new BigDecimal("500000"), "UZS", LocalDate.now(), "CTR-1", "uz", null, null));
        when(ariService.originate(eq("998901234567"), eq(1L), any(OutboundCall.class)))
                .thenThrow(new ConflictException(ErrorCode.CALL_NUMBER_ON_DO_NOT_CALL_LIST, "998901234567"));

        consumer.process(task, dialerState);

        verify(callRecordService).recordUnplacedAttempt(1L, 100L, "998901234567", "uz",
                Disposition.DO_NOT_CALL, "number is in the do-not-call list");
        verify(campaignTargetUseCase).applyOutcome(1L, 100L, Disposition.DO_NOT_CALL);
        verify(campaignTargetUseCase, never()).applyOutcome(1L, 100L, Disposition.FAILED);
    }

    @Test
    void successfullyOriginatesAndPassesOutboundCallAtomically() {
        CallTask task = new CallTask(1L, 100L, 200L, "998901234567", "uz", "{}", 1L, 7L,
                null, null, null, null, null);

        ScenarioDefinition def = new ScenarioDefinition(List.of(), List.of(), List.of(), List.of(), "system prompt", List.of(), "disclosure");
        Scenario scenario = new Scenario(10L, "test-scenario", 1, "Test", "Desc", false, true, def, Instant.now(), 1L);
        when(aiAgents.requireCallAgent(1L, 7L, "998901234567")).thenReturn(AiAgentFixtures.agent(7L, 1L, 10L, "uz", "dilfuza"));
        when(aiAgents.resolveScenario(eq(1L), any())).thenReturn(scenario);
        when(crmClient.fetchClient(1L, 200L)).thenReturn(new CrmClientSnapshot("Ali", new BigDecimal("500000"), "UZS", LocalDate.now(), "CTR-1", "uz", null, null));
        when(ariService.originate(eq("998901234567"), eq(1L), any(OutboundCall.class))).thenReturn("chan-123");

        consumer.process(task, dialerState);

        ArgumentCaptor<OutboundCall> captor = ArgumentCaptor.forClass(OutboundCall.class);
        verify(ariService).originate(eq("998901234567"), eq(1L), captor.capture());
        OutboundCall passedCall = captor.getValue();
        assertNotNull(passedCall);
        assertEquals(100L, passedCall.targetId());
        assertEquals("998901234567", passedCall.phone());
        assertEquals("uz", passedCall.language());
        assertEquals("dilfuza", passedCall.ttsVoice());

        verify(audit).record(eq(1L), eq("CALL_ORIGINATE"), eq("call"), eq("chan-123"), anyString());
    }

    @Test
    void scrubsInvalidPhoneNumberBeforeOrigination() {
        // +99801 is an unallocated/impossible prefix in Uzbekistan
        CallTask task = new CallTask(1L, 101L, 201L, "+998011234567", "uz", "{}", 1L, 7L,
                null, null, null, null, null);

        consumer.process(task, dialerState);

        verify(ariService, never()).originate(anyString(), anyLong(), any(OutboundCall.class));
        verify(dialerState).release(1L, 1L);
        verify(callRecordService).recordUnplacedAttempt(eq(1L), eq(101L), eq("+998011234567"),
                eq("uz"), eq(Disposition.CARRIER_REJECTED), contains("scrubbed"));
        verify(campaignTargetUseCase).applyOutcome(1L, 101L, Disposition.CARRIER_REJECTED);
    }

    @Test
    void defersCallWhenOutsideRecipientDialWindow() {
        CallTask task = new CallTask(1L, 102L, 202L, "+998901234567", "uz", "{}", 1L, 7L,
                null, null, null, null, null);
        Campaign activeCampaign = Campaign.builder()
                .id(1L)
                .name("c")
                .status(CampaignStatus.ACTIVE)
                .schedule(new CampaignSchedule(null, null, LocalTime.of(9, 0), LocalTime.of(20, 0), Set.of()))
                .retry(new CampaignRetry(3, 24, null))
                .limits(new CampaignLimits(5, 0, null, null, null))
                .aiAgentId(7L)
                .companyId(1L)
                .recurrence(new CampaignRecurrence(RecurrenceType.ONCE, null, null, false))
                .build();
        when(campaignUseCase.findCampaign(1L, 1L)).thenReturn(activeCampaign);
        when(dialerService.isWithinDialWindow(activeCampaign)).thenReturn(true);
        when(dialerService.isWithinDialWindowForRecipient(activeCampaign, "+998901234567")).thenReturn(false);

        consumer.process(task, dialerState);

        verify(dialerState).release(1L, 1L);
        verify(campaignTargetUseCase).releaseClaim(eq(102L), any(Instant.class));
        verify(ariService, never()).originate(anyString(), anyLong(), any(OutboundCall.class));
    }
}
