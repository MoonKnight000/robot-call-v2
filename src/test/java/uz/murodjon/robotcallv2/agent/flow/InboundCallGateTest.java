package uz.murodjon.robotcallv2.agent.flow;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import uz.murodjon.robotcallv2.billing.application.port.input.CallBillingUseCase;
import uz.murodjon.robotcallv2.callflow.application.port.input.CallFlowUseCase;
import uz.murodjon.robotcallv2.company.application.port.input.CompanyConfigUseCase;
import uz.murodjon.robotcallv2.company.application.port.input.CompanyUseCase;
import uz.murodjon.robotcallv2.company.domain.entity.Company;
import uz.murodjon.robotcallv2.company.domain.enums.CompanyStatus;
import uz.murodjon.robotcallv2.inbound.application.port.input.InboundCallerRuleUseCase;
import uz.murodjon.robotcallv2.inbound.application.port.input.InboundRouteUseCase;
import uz.murodjon.robotcallv2.inbound.domain.entity.InboundRoute;
import uz.murodjon.robotcallv2.inbound.domain.enums.InboundAfterHoursAction;
import uz.murodjon.robotcallv2.schedule.application.port.input.BusinessScheduleUseCase;
import uz.murodjon.robotcallv2.schedule.domain.entity.BusinessHoursVerdict;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalTime;
import java.time.ZoneId;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class InboundCallGateTest {

    @Mock
    private InboundRouteUseCase inboundRouteUseCase;
    @Mock
    private InboundCallerRuleUseCase callerRuleUseCase;
    @Mock
    private CallFlowUseCase callFlowUseCase;
    @Mock
    private BusinessScheduleUseCase businessScheduleUseCase;
    @Mock
    private CompanyConfigUseCase companyConfigService;
    @Mock
    private CompanyUseCase companyUseCase;
    @Mock
    private CallBillingUseCase callBillingUseCase;

    private Clock clock;
    private InboundCallGate gate;

    @BeforeEach
    void setUp() {
        clock = Clock.fixed(Instant.parse("2026-10-10T12:00:00Z"), ZoneId.of("UTC"));
        gate = new InboundCallGate(
                inboundRouteUseCase, callerRuleUseCase, callFlowUseCase,
                businessScheduleUseCase, companyConfigService, companyUseCase,
                callBillingUseCase, clock
        );
        when(companyUseCase.findById(anyLong())).thenReturn(new Company(1L, "Test Co", CompanyStatus.ACTIVE, Instant.now(), null, null, null));
        when(callBillingUseCase.hasBalanceForCall(anyLong())).thenReturn(true);
        when(companyConfigService.resolveLanguage(anyLong(), any())).thenReturn("uz-UZ");
        when(businessScheduleUseCase.findZone(anyLong(), any())).thenReturn(ZoneId.of("Asia/Tashkent"));
    }

    private InboundRoute createRoute(InboundAfterHoursAction afterHoursAction, String afterHoursDestination) {
        return new InboundRoute(
                10L, 1L, "+998712001122", 5L, afterHoursAction, afterHoursDestination,
                LocalTime.of(9, 0), LocalTime.of(18, 0), null,
                null, null, null, true, Instant.now()
        );
    }

    @Test
    @DisplayName("Routes to primary AI agent when business hours are open")
    void routesToAgentWhenOpen() {
        InboundRoute route = createRoute(InboundAfterHoursAction.PLAY_MESSAGE_AND_HANGUP, null);
        when(inboundRouteUseCase.resolveByDid("+998712001122")).thenReturn(route);
        when(businessScheduleUseCase.evaluateDailyWindow(eq(1L), any(), any(), any()))
                .thenReturn(new BusinessHoursVerdict(true, null, null));

        InboundDecision decision = gate.decide("+998712001122", "+998901234567");

        assertEquals(InboundDecisionKind.AGENT, decision.kind());
        assertEquals(5L, decision.route().aiAgentId());
    }

    @Test
    @DisplayName("Routes to night AI agent when outside hours and afterHoursAction is AI_AGENT")
    void routesToNightAgentWhenClosedAndActionIsAiAgent() {
        InboundRoute route = createRoute(InboundAfterHoursAction.AI_AGENT, "88");
        when(inboundRouteUseCase.resolveByDid("+998712001122")).thenReturn(route);
        when(businessScheduleUseCase.evaluateDailyWindow(eq(1L), any(), any(), any()))
                .thenReturn(new BusinessHoursVerdict(false, null, java.time.ZonedDateTime.parse("2026-10-11T09:00:00+05:00[Asia/Tashkent]")));

        InboundDecision decision = gate.decide("+998712001122", "+998901234567");

        assertEquals(InboundDecisionKind.AGENT, decision.kind());
        assertNotNull(decision.route());
        assertEquals(88L, decision.route().aiAgentId());
    }

    @Test
    @DisplayName("Returns closed with voicemail prompt when afterHoursAction is VOICEMAIL")
    void routesToVoicemailClosedWhenAfterHoursActionIsVoicemail() {
        InboundRoute route = createRoute(InboundAfterHoursAction.VOICEMAIL, "2");
        when(inboundRouteUseCase.resolveByDid("+998712001122")).thenReturn(route);
        when(businessScheduleUseCase.evaluateDailyWindow(eq(1L), any(), any(), any()))
                .thenReturn(new BusinessHoursVerdict(false, null, java.time.ZonedDateTime.parse("2026-10-11T09:00:00+05:00[Asia/Tashkent]")));

        InboundDecision decision = gate.decide("+998712001122", "+998901234567");

        assertEquals(InboundDecisionKind.CLOSED, decision.kind());
        assertTrue(decision.afterHours());
        assertNotNull(decision.message());
        assertTrue(decision.message().contains("xabar"));
    }
}
