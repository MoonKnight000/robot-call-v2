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
import uz.murodjon.robotcallv2.callrecord.application.service.CallRecordService;
import uz.murodjon.robotcallv2.campaign.application.service.CampaignService;
import uz.murodjon.robotcallv2.crm.application.service.CrmClient;
import uz.murodjon.robotcallv2.crm.domain.entity.CrmClientSnapshot;
import uz.murodjon.robotcallv2.dialer.application.dto.CallTask;
import uz.murodjon.robotcallv2.dialer.application.dto.OutboundCall;
import uz.murodjon.robotcallv2.donotcall.application.port.output.DoNotCallRepository;
import uz.murodjon.robotcallv2.memory.application.service.ClientMemoryService;
import uz.murodjon.robotcallv2.scenario.application.service.FactWebhookClient;
import uz.murodjon.robotcallv2.scenario.application.service.ScenarioService;
import uz.murodjon.robotcallv2.scenario.domain.entity.Scenario;
import uz.murodjon.robotcallv2.scenario.domain.entity.ScenarioDefinition;
import uz.murodjon.robotcallv2.shared.dialog.Disposition;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

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
    private ScenarioService scenarioService;
    @Mock
    private CrmClient crmClient;
    @Mock
    private DoNotCallRepository doNotCallRepository;
    @Mock
    private AuditService audit;
    @Mock
    private DialerState dialerState;
    @Mock
    private CallRecordService callRecordService;
    @Mock
    private ClientMemoryService clientMemoryService;
    @Mock
    private AiAgentUseCase aiAgents;

    private CallTaskConsumer consumer;

    @BeforeEach
    void setUp() {
        consumer = new CallTaskConsumer(
                ariService, callRegistry, campaignService,
                scenarioService, crmClient, audit, dialerState, doNotCallRepository, callRecordService,
                clientMemoryService, mock(FactWebhookClient.class), aiAgents,
                mock(CallBillingUseCase.class)
        );
    }

    @Test
    void dropsBlockedNumberAndReleasesDialerState() {
        CallTask task = new CallTask(1L, 100L, 200L, "998901234567", "uz", "{}", 1L, 7L,
                null, null, null, null);

        when(doNotCallRepository.isBlocked(1L, "998901234567")).thenReturn(true);

        consumer.process(task, dialerState);

        verify(dialerState).release(1L);
        verify(campaignService).applyOutcome(100L, Disposition.DO_NOT_CALL);
        verify(ariService, never()).originate(anyString(), anyLong(), any(OutboundCall.class));
    }

    @Test
    void successfullyOriginatesAndPassesOutboundCallAtomically() {
        CallTask task = new CallTask(1L, 100L, 200L, "998901234567", "uz", "{}", 1L, 7L,
                null, null, null, null);

        ScenarioDefinition def = new ScenarioDefinition(List.of(), List.of(), List.of(), List.of(), "system prompt", List.of(), "disclosure");
        Scenario scenario = new Scenario(10L, "test-scenario", 1, "Test", "Desc", false, true, def, Instant.now(), 1L);
        when(aiAgents.requireAgent(1L, 7L)).thenReturn(AiAgentFixtures.agent(7L, 1L, 10L, "uz", "dilfuza"));
        when(scenarioService.requireScenario(10L)).thenReturn(scenario);
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

        verify(audit).record(eq("CALL_ORIGINATE"), eq("call"), eq("chan-123"), anyString());
    }
}
