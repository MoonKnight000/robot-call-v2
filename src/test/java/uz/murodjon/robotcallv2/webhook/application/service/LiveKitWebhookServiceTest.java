package uz.murodjon.robotcallv2.webhook.application.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import uz.murodjon.robotcallv2.agent.ari.LiveKitCallService;
import uz.murodjon.robotcallv2.agent.flow.InboundCallGate;
import uz.murodjon.robotcallv2.agent.flow.InboundDecision;
import uz.murodjon.robotcallv2.agent.livekit.LiveKitTelephonyService;
import uz.murodjon.robotcallv2.inbound.domain.entity.InboundRoute;
import uz.murodjon.robotcallv2.shared.exception.ErrorCode;
import uz.murodjon.robotcallv2.shared.exception.ForbiddenException;
import uz.murodjon.robotcallv2.shared.exception.ValidationException;

import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class LiveKitWebhookServiceTest {

    private static final String DID = "998712000000";
    private static final String CALLER = "998901234567";

    private InboundCallGate inboundCallGate;
    private LiveKitTelephonyService liveKitTelephonyService;
    private LiveKitCallService liveKitCallService;
    private LiveKitWebhookService service;

    @BeforeEach
    void setUp() {
        inboundCallGate = mock(InboundCallGate.class);
        liveKitTelephonyService = mock(LiveKitTelephonyService.class);
        liveKitCallService = mock(LiveKitCallService.class);
        service = new LiveKitWebhookService(inboundCallGate, liveKitTelephonyService, liveKitCallService, new ObjectMapper());
    }

    @Test
    void unsignedWebhookIsRefusedBeforeAnythingIsRead() {
        byte[] body = sipEvent("participant_joined", "inbound-abc", null);
        doThrow(new ForbiddenException(ErrorCode.LIVEKIT_WEBHOOK_SIGNATURE_INVALID))
                .when(liveKitTelephonyService).verifyWebhook(null, body);

        assertThrows(ForbiddenException.class, () -> service.handleEvent(null, body));
        verifyNoInteractions(inboundCallGate);
    }

    @Test
    void inboundCallerRoutedToAgentIsHandedToTheAgent() {
        InboundRoute route = mock(InboundRoute.class);
        when(inboundCallGate.decide(DID, CALLER)).thenReturn(InboundDecision.agent(route, "uz-UZ", false));

        service.handleEvent("token", sipEvent("participant_joined", "inbound-abc", null));

        verify(liveKitCallService).startInboundCall("inbound-abc", route, CALLER, DID);
        verify(liveKitTelephonyService, never()).deleteRoom(anyString());
    }

    @Test
    void inboundCallerWithNoRouteIsHungUp() {
        when(inboundCallGate.decide(DID, CALLER)).thenReturn(InboundDecision.noRoute());

        service.handleEvent("token", sipEvent("participant_joined", "inbound-abc", null));

        verify(liveKitTelephonyService).deleteRoom("inbound-abc");
    }

    @Test
    void phoneLegLeavingEndsTheCall() {
        service.handleEvent("token", sipEvent("participant_left", "livekit-42", "USER_UNAVAILABLE"));

        verify(liveKitCallService).onPhoneLegLeft("livekit-42", "sip-caller", "USER_UNAVAILABLE");
        verifyNoInteractions(inboundCallGate);
    }

    @Test
    void nonSipParticipantIsIgnored() {
        byte[] body = """
                {"event":"participant_joined","room":{"name":"inbound-abc"},
                 "participant":{"identity":"agent","kind":"AGENT"}}""".getBytes(StandardCharsets.UTF_8);

        service.handleEvent("token", body);

        verifyNoInteractions(inboundCallGate);
        verify(liveKitTelephonyService, never()).deleteRoom(anyString());
        verifyNoInteractions(liveKitCallService);
    }

    @Test
    void transferLegJoiningAnInboundRoomIsNotACaller() {
        byte[] body = """
                {"event":"participant_joined","room":{"name":"inbound-abc"},
                 "participant":{"identity":"sip-operator","kind":"SIP",
                  "attributes":{"sip.phoneNumber":"998901112233"}}}""".getBytes(StandardCharsets.UTF_8);

        service.handleEvent("token", body);

        verifyNoInteractions(inboundCallGate);
        verify(liveKitTelephonyService, never()).deleteRoom(anyString());
    }

    @Test
    void bodyThatIsNotJsonIsRejected() {
        byte[] body = "not json".getBytes(StandardCharsets.UTF_8);

        assertThrows(ValidationException.class, () -> service.handleEvent("token", body));
    }

    private static byte[] sipEvent(String event, String room, String disconnectReason) {
        String reason = disconnectReason != null ? ",\"disconnectReason\":\"" + disconnectReason + "\"" : "";
        return ("{\"event\":\"" + event + "\",\"room\":{\"name\":\"" + room + "\"},"
                + "\"participant\":{\"identity\":\"sip-caller\",\"kind\":\"SIP\","
                + "\"attributes\":{\"sip.phoneNumber\":\"" + CALLER + "\",\"sip.trunkPhoneNumber\":\"" + DID + "\"}"
                + reason + "}}").getBytes(StandardCharsets.UTF_8);
    }
}
