package uz.murodjon.robotcallv2.agent.livekit;

import com.fasterxml.jackson.databind.JsonNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import uz.murodjon.robotcallv2.agent.telephony.WebRtcJoin;
import uz.murodjon.robotcallv2.shared.exception.ConflictException;
import uz.murodjon.robotcallv2.shared.exception.ErrorCode;
import uz.murodjon.robotcallv2.shared.exception.ExternalServiceException;
import uz.murodjon.robotcallv2.shared.util.PhoneMasker;
import uz.murodjon.robotcallv2.siptrunk.domain.entity.SipTrunkCredentials;

import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.HashMap;
import java.util.Map;

/**
 * The LiveKit telephony engine: one LiveKit room per call, the phone leg joined to it as a
 * SIP participant by the LiveKit SIP service (docker-compose {@code livekit-sip}).
 *
 * <p>Holds no per-call state: the room name is the call's channel id, so nothing has to be
 * cleaned up when a call ends. Outbound rooms are {@link #OUTBOUND_ROOM_PREFIX}{@code <callId>};
 * inbound ones get {@link #INBOUND_ROOM_PREFIX} from the SIP dispatch rule (docs/LIVEKIT.md).
 */
@Service
public class LiveKitTelephonyService {

    public static final String OUTBOUND_ROOM_PREFIX = "livekit-";
    public static final String INBOUND_ROOM_PREFIX = "inbound-";
    /** The phone leg of an outbound call. */
    public static final String CALLEE_IDENTITY = "sip-callee";
    /** The leg a transfer rings: the person the agent hands the call to. */
    public static final String OPERATOR_IDENTITY = "sip-operator";
    /** An operator who joins from the browser: {@code op-<userId>}. */
    public static final String OPERATOR_WEB_PREFIX = "op-";
    /** A supervisor who watches from the browser: {@code sv-<userId>}. */
    public static final String SUPERVISOR_WEB_PREFIX = "sv-";
    /** A browser test or website widget call: the room is {@code web-<sessionId>}. */
    public static final String BROWSER_ROOM_PREFIX = "web-";
    /** The person in a browser test or widget call, who is the caller. */
    public static final String BROWSER_IDENTITY = "browser-caller";

    private static final Logger log = LoggerFactory.getLogger(LiveKitTelephonyService.class);
    /** A room nobody joins (a dispatch that never rang) closes itself after this long. */
    private static final int ROOM_EMPTY_TIMEOUT_SEC = 60;
    /** ... and one everybody has left closes after this long. */
    private static final int ROOM_DEPARTURE_TIMEOUT_SEC = 5;
    /** On top of the ringing: the request also covers the INVITE going out and the answer coming back. */
    private static final Duration DIAL_REQUEST_MARGIN = Duration.ofSeconds(15);
    private static final int DEFAULT_SIP_PORT = 5060;

    private final LiveKitProperties properties;
    private final LiveKitApiClient apiClient;

    public LiveKitTelephonyService(LiveKitProperties properties, LiveKitApiClient apiClient) {
        this.properties = properties;
        this.apiClient = apiClient;
    }

    public boolean isEnabled() {
        return properties.enabled();
    }

    /** Whether {@code channelId} names a LiveKit call's room rather than an Asterisk channel. */
    public static boolean isLiveKitRoom(String channelId) {
        return channelId != null
                && (channelId.startsWith(OUTBOUND_ROOM_PREFIX) || channelId.startsWith(INBOUND_ROOM_PREFIX)
                || channelId.startsWith(BROWSER_ROOM_PREFIX));
    }

    /** Creates the LiveKit room for a call session, named exactly {@code roomName}. */
    public String createCallRoom(String roomName) {
        requireEnabled();
        apiClient.call("RoomService", "CreateRoom", Map.of(
                "name", roomName,
                "emptyTimeout", ROOM_EMPTY_TIMEOUT_SEC,
                "departureTimeout", ROOM_DEPARTURE_TIMEOUT_SEC));
        log.info("LiveKit: room {} created", roomName);
        return roomName;
    }

    /** What the browser of operator {@code userId} needs to join {@code roomName} and take the call. */
    public WebRtcJoin createOperatorJoin(String roomName, long userId) {
        String publicUrl = requirePublicUrl();
        String identity = OPERATOR_WEB_PREFIX + userId;
        return new WebRtcJoin(publicUrl, apiClient.createJoinToken(roomName, identity, "Operator", true, false), identity);
    }

    /**
     * What the browser of supervisor {@code userId} needs to watch {@code roomName}: unseen and
     * unheard when only listening, speaking into the call when {@code speak}.
     */
    public WebRtcJoin createSupervisorJoin(String roomName, long userId, boolean speak) {
        String publicUrl = requirePublicUrl();
        String identity = SUPERVISOR_WEB_PREFIX + userId;
        return new WebRtcJoin(publicUrl,
                apiClient.createJoinToken(roomName, identity, "Supervisor", speak, !speak), identity);
    }

    /**
     * The room of a browser test or widget call and what the caller's browser joins it with. The call starts
     * when the browser joins ({@code LiveKitCallService#onBrowserCallerJoined}), claiming the waiting session.
     */
    public WebRtcJoin createBrowserCallJoin(String sessionId) {
        String publicUrl = requirePublicUrl();
        String roomName = BROWSER_ROOM_PREFIX + sessionId;
        createCallRoom(roomName);
        return new WebRtcJoin(publicUrl, apiClient.createJoinToken(roomName, BROWSER_IDENTITY, "Caller", true, false),
                BROWSER_IDENTITY);
    }

    /** Closes the room, which hangs up every participant in it — the phone leg included. */
    public void deleteRoom(String roomName) {
        requireEnabled();
        apiClient.call("RoomService", "DeleteRoom", Map.of("room", roomName));
        log.info("LiveKit: room {} deleted", roomName);
    }

    /** Hangs up one leg of the room and leaves the rest of the call as it is. */
    public void removeParticipant(String roomName, String identity) {
        try {
            apiClient.call("RoomService", "RemoveParticipant", Map.of("room", roomName, "identity", identity));
            log.info("LiveKit: {} removed from room {}", identity, roomName);
        } catch (RuntimeException e) {
            log.warn("LiveKit: removing {} from room {} failed: {}", identity, roomName, e.getMessage());
        }
    }

    /**
     * {@link #deleteRoom} for the hang-up paths, which run where nobody can act on a failure
     * (a dialog ending, a teardown, shutdown): a room already gone is the expected case.
     */
    public void hangUp(String roomName) {
        try {
            deleteRoom(roomName);
        } catch (RuntimeException e) {
            log.warn("LiveKit: hang-up of room {} failed: {}", roomName, e.getMessage());
        }
    }

    /**
     * Dials {@code calleeNumber} into {@code roomName} and blocks until the callee answers or
     * the call fails — up to {@code answerTimeoutSec} of ringing. Run it off the dialer's thread.
     * Refused when LiveKit is switched off here: reporting a call as placed when nothing rings is
     * worse than failing it.
     *
     * @param identity the participant the leg becomes in the room ({@link #CALLEE_IDENTITY},
     *                 {@link #OPERATOR_IDENTITY})
     * @param callerId A-number shown to the callee; blank leaves the trunk's own
     * @param trunk    the company's own trunk, sent inline with the call so nothing is kept in
     *                 LiveKit; null dials the platform's LiveKit trunk ({@code LIVEKIT_SIP_TRUNK_ID})
     * @param sipHeaders custom headers put on the leg's INVITE as they are; empty for none
     */
    public LiveKitDialOutcome dialAndWaitForAnswer(String roomName, String identity, String calleeNumber,
                                                   String callerId, SipTrunkCredentials trunk,
                                                   Map<String, String> sipHeaders) {
        requireEnabled();
        Map<String, Object> body = new HashMap<>();
        if (trunk != null) {
            body.put("trunk", toOutboundConfig(trunk));
        } else if (properties.sipTrunkId() != null && !properties.sipTrunkId().isBlank()) {
            body.put("sipTrunkId", properties.sipTrunkId());
        } else {
            throw new ConflictException(ErrorCode.LIVEKIT_NOT_CONFIGURED, "LIVEKIT_SIP_TRUNK_ID");
        }
        body.put("sipCallTo", calleeNumber);
        body.put("roomName", roomName);
        body.put("participantIdentity", identity);
        body.put("participantName", PhoneMasker.mask(calleeNumber));
        body.put("waitUntilAnswered", true);
        body.put("ringingTimeout", properties.answerTimeoutSec() + "s");
        if (callerId != null && !callerId.isBlank()) {
            body.put("sipNumber", callerId);
        }
        if (sipHeaders != null && !sipHeaders.isEmpty()) {
            body.put("headers", sipHeaders);
        }
        log.info("LiveKit: dialling {} from {} in room {} over {}", PhoneMasker.mask(calleeNumber), callerId, roomName,
                trunk != null ? "company trunk " + trunk.trunkId() : "trunk " + properties.sipTrunkId());
        HttpResponse<String> response = apiClient.send("SIP", "CreateSIPParticipant", body,
                Duration.ofSeconds(properties.answerTimeoutSec()).plus(DIAL_REQUEST_MARGIN));
        JsonNode json = apiClient.readBody("SIP/CreateSIPParticipant", response.body());
        if (response.statusCode() / 100 == 2) {
            return LiveKitDialOutcome.answeredCall();
        }
        // A call the far end turned down is a Twirp error carrying the SIP status in its
        // meta; an error without one is LiveKit itself failing.
        JsonNode status = json.path("meta").path("sip_status_code");
        if (status.isMissingNode()) {
            throw new ExternalServiceException(ErrorCode.LIVEKIT_REQUEST_FAILED, "livekit", "SIP/CreateSIPParticipant",
                    "HTTP " + response.statusCode() + " " + response.body());
        }
        return LiveKitDialOutcome.unanswered(status.asInt(0));
    }

    /** {@code livekit.SIPOutboundConfig}: a trunk given with the call instead of stored as an ST_ id. */
    private static Map<String, Object> toOutboundConfig(SipTrunkCredentials trunk) {
        Map<String, Object> config = new HashMap<>();
        config.put("hostname", trunk.port() > 0 && trunk.port() != DEFAULT_SIP_PORT ? trunk.host() + ":" + trunk.port() : trunk.host());
        config.put("authUsername", trunk.username());
        config.put("authPassword", trunk.password());
        if (trunk.transport() != null) {
            config.put("transport", "SIP_TRANSPORT_" + trunk.transport().name());
        }
        return config;
    }

    /** Throws {@code ForbiddenException} unless the webhook was signed by our LiveKit server. */
    public void verifyWebhook(String authorization, byte[] body) {
        apiClient.verifyWebhook(authorization, body);
    }

    /** Throws {@code ForbiddenException} unless the request was signed by our media bridge. */
    public void verifyBridgeSignature(String signature, byte[] body) {
        apiClient.verifyBridgeSignature(signature, body);
    }

    /** The address browsers connect to; they cannot join a room without it. */
    private String requirePublicUrl() {
        requireEnabled();
        String publicUrl = properties.publicUrl();
        if (publicUrl == null || publicUrl.isBlank()) {
            throw new ConflictException(ErrorCode.LIVEKIT_NOT_CONFIGURED, "LIVEKIT_PUBLIC_URL");
        }
        return publicUrl;
    }

    private void requireEnabled() {
        if (!properties.enabled()) {
            throw new ConflictException(ErrorCode.LIVEKIT_DISABLED);
        }
    }
}
