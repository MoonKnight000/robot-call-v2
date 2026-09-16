package uz.murodjon.robotcallv2.dialer.application.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import uz.murodjon.robotcallv2.agent.ari.AriService;
import uz.murodjon.robotcallv2.aiagent.AiAgentFixtures;
import uz.murodjon.robotcallv2.aiagent.application.port.input.AiAgentUseCase;
import uz.murodjon.robotcallv2.audit.application.service.AuditService;
import uz.murodjon.robotcallv2.billing.application.port.input.CallBillingUseCase;
import uz.murodjon.robotcallv2.callrecord.application.port.input.CallAttemptUseCase;
import uz.murodjon.robotcallv2.campaign.application.service.CampaignService;
import uz.murodjon.robotcallv2.campaign.domain.entity.Campaign;
import uz.murodjon.robotcallv2.campaign.domain.enums.CampaignStatus;
import uz.murodjon.robotcallv2.campaign.domain.enums.CampaignType;
import uz.murodjon.robotcallv2.campaign.domain.enums.RecurrenceType;
import uz.murodjon.robotcallv2.company.application.port.input.CompanyUseCase;
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

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
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
    private CampaignService campaignService;
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

    private CallTaskConsumer consumer;

    @BeforeEach
    void setUp() {
        consumer = new CallTaskConsumer(
                ariService, callRegistry, campaignService, campaignService,
                crmClient, audit, dialerState, doNotCallUseCase, callRecordService,
                clientMemoryService, mock(FactWebhookUseCase.class), aiAgents,
                mock(CallBillingUseCase.class), dialerService, mock(CompanyUseCase.class)
        );
    }

    /** Paused while the task waited in the queue: nothing is dialled and the claim goes back, attempt and all. */
    @Test
    void aTaskForAPausedCampaignIsGivenBackWithoutDialling() {
        CallTask task = new CallTask(1L, 100L, 200L, "998901234567", "uz", "{}", 1L, 7L,
                null, null, null, null, null);
        when(campaignService.findCampaign(1L, 1L)).thenReturn(new Campaign(
                1L, "c", CampaignType.DEBT_COLLECTION, CampaignStatus.PAUSED,
                LocalTime.of(9, 0), LocalTime.of(20, 0), Set.of(), 3, 24, 5, 0, 7L, 1L, null,
                RecurrenceType.ONCE, null, null, false, null));

        consumer.process(task, dialerState);

        verify(dialerState).release(1L, 1L);
        verify(campaignService).releaseClaim(eq(100L), any(Instant.class));
        verify(campaignService, never()).applyOutcome(anyLong(), anyLong(), any());
        verify(ariService, never()).originate(anyString(), anyLong(), any(OutboundCall.class));
    }

    @Test
    void dropsBlockedNumberAndReleasesDialerState() {
        CallTask task = new CallTask(1L, 100L, 200L, "998901234567", "uz", "{}", 1L, 7L,
                null, null, null, null, null);

        when(doNotCallUseCase.contains(1L, "998901234567")).thenReturn(true);

        consumer.process(task, dialerState);

        verify(dialerState).release(1L, 1L);
        verify(campaignService).applyOutcome(1L, 100L, Disposition.DO_NOT_CALL);
        verify(ariService, never()).originate(anyString(), anyLong(), any(OutboundCall.class));
    }

    @Test
    void successfullyOriginatesAndPassesOutboundCallAtomically() {
        CallTask task = new CallTask(1L, 100L, 200L, "998901234567", "uz", "{}", 1L, 7L,
                null, null, null, null, null);

        ScenarioDefinition def = new ScenarioDefinition(List.of(), List.of(), List.of(), List.of(), "system prompt", List.of(), "disclosure");
        Scenario scenario = new Scenario(10L, "test-scenario", 1, "Test", "Desc", false, true, def, Instant.now(), 1L);
        when(aiAgents.requirePublishedAgent(1L, 7L)).thenReturn(AiAgentFixtures.agent(7L, 1L, 10L, "uz", "dilfuza"));
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
}
