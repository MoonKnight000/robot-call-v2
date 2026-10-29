package uz.murodjon.robotcallv2.webhook.application.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import uz.murodjon.robotcallv2.agent.flow.InboundCallGate;
import uz.murodjon.robotcallv2.agent.flow.InboundDecision;
import uz.murodjon.robotcallv2.agent.livekit.LiveKitTelephonyService;
import uz.murodjon.robotcallv2.inbound.domain.entity.InboundRoute;
import uz.murodjon.robotcallv2.webhook.application.dto.LiveKitSipWebhookRequest;
import uz.murodjon.robotcallv2.webhook.application.dto.LiveKitSipWebhookResponse;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class LiveKitWebhookServiceTest {

    private InboundCallGate inboundCallGate;
    private LiveKitTelephonyService liveKitTelephonyService;
    private LiveKitWebhookService service;

    @BeforeEach
    void setUp() {
        inboundCallGate = mock(InboundCallGate.class);
        liveKitTelephonyService = mock(LiveKitTelephonyService.class);
        service = new LiveKitWebhookService(inboundCallGate, liveKitTelephonyService);
    }

    @Test
    void handleSipWebhookNullRequestReturnsReject() {
        LiveKitSipWebhookResponse response = service.handleSipWebhook(null);
        assertEquals("REJECT", response.action());
        assertNotNull(response.message());
    }

    @Test
    void handleSipWebhookAgentRouteDispatchesCall() {
        InboundRoute route = mock(InboundRoute.class);
        when(route.id()).thenReturn(10L);
        when(route.aiAgentId()).thenReturn(20L);

        InboundDecision decision = InboundDecision.agent(route, "uz-UZ", true);
        when(inboundCallGate.decide("+998712000000", "+998901234567")).thenReturn(decision);
        when(liveKitTelephonyService.isEnabled()).thenReturn(true);

        LiveKitSipWebhookRequest request = new LiveKitSipWebhookRequest(
                "sip-call-123", "+998901234567", "+998712000000", "my-room", "trunk-1"
        );

        LiveKitSipWebhookResponse response = service.handleSipWebhook(request);

        assertEquals("ACCEPT", response.action());
        assertEquals("my-room", response.roomName());
        assertEquals(10L, response.routeId());
        assertEquals(20L, response.agentId());
        assertNull(response.callFlowId());
        assertEquals("uz-UZ", response.language());
        assertTrue(response.vip());
        verify(liveKitTelephonyService).createCallRoom("my-room");
    }

    @Test
    void handleSipWebhookFlowRouteDispatchesCall() {
        InboundRoute route = mock(InboundRoute.class);
        when(route.id()).thenReturn(15L);
        when(route.callFlowId()).thenReturn(35L);

        InboundDecision decision = InboundDecision.flow(route, null, "ru-RU", false);
        when(inboundCallGate.decide("+998712000000", "+998901234567")).thenReturn(decision);

        LiveKitSipWebhookRequest request = new LiveKitSipWebhookRequest(
                "sip-call-456", "+998901234567", "+998712000000", null, null
        );

        LiveKitSipWebhookResponse response = service.handleSipWebhook(request);

        assertEquals("ACCEPT", response.action());
        assertEquals("inbound-sip-call-456", response.roomName());
        assertEquals(15L, response.routeId());
        assertNull(response.agentId());
        assertEquals(35L, response.callFlowId());
        assertEquals("ru-RU", response.language());
        assertFalse(response.vip());
    }

    @Test
    void handleSipWebhookNoRouteReturnsReject() {
        when(inboundCallGate.decide("+998712000000", "+998901234567")).thenReturn(InboundDecision.noRoute());

        LiveKitSipWebhookRequest request = new LiveKitSipWebhookRequest(
                "sip-call-789", "+998901234567", "+998712000000", null, null
        );

        LiveKitSipWebhookResponse response = service.handleSipWebhook(request);

        assertEquals("REJECT", response.action());
        assertTrue(response.message().contains("No route found"));
    }

    @Test
    void handleSipWebhookClosedReturnsReject() {
        InboundRoute route = mock(InboundRoute.class);
        InboundDecision decision = InboundDecision.closed(route, "Kompaniya hozir ishlamayapti", "uz-UZ", false, true);
        when(inboundCallGate.decide("+998712000000", "+998901234567")).thenReturn(decision);

        LiveKitSipWebhookRequest request = new LiveKitSipWebhookRequest(
                "sip-call-999", "+998901234567", "+998712000000", null, null
        );

        LiveKitSipWebhookResponse response = service.handleSipWebhook(request);

        assertEquals("REJECT", response.action());
        assertEquals("Kompaniya hozir ishlamayapti", response.message());
    }
}
