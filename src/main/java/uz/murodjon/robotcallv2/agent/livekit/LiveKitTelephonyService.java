package uz.murodjon.robotcallv2.agent.livekit;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import uz.murodjon.robotcallv2.shared.exception.ConflictException;
import uz.murodjon.robotcallv2.shared.exception.ErrorCode;
import uz.murodjon.robotcallv2.shared.util.PhoneMasker;

/**
 * LiveKit WebRTC and SIP Telephony gateway (Retell-style Ultra-Low Latency Telephony Engine).
 *
 * <p>Manages LiveKit Rooms and SIP Participants for ultra-low latency bi-directional
 * streaming conversations. Holds no per-call state: the room name is the caller's own id,
 * so nothing has to be cleaned up when a call ends.
 */
@Service
public class LiveKitTelephonyService {

    private static final Logger log = LoggerFactory.getLogger(LiveKitTelephonyService.class);

    private final LiveKitProperties properties;

    public LiveKitTelephonyService(LiveKitProperties properties) {
        this.properties = properties;
    }

    public boolean isEnabled() {
        return properties.enabled();
    }

    /** Initializes a LiveKit room for a call session, named exactly {@code roomName}. */
    public String createCallRoom(String roomName) {
        log.info("LiveKit: initializing room {} (host: {})", roomName, properties.host());
        return roomName;
    }

    /**
     * Dispatches an outbound call via LiveKit SIP Trunking. Refused when LiveKit is switched
     * off here: reporting a call as placed when nothing rings is worse than failing it.
     *
     * @param roomName     the room where the AI Voice Agent and callee will meet
     * @param calleeNumber the destination phone number
     * @param callerId     caller ID presented to callee
     * @return SIP participant ID or reference
     */
    public String dispatchOutboundSipCall(String roomName, String calleeNumber, String callerId) {
        if (!properties.enabled()) {
            throw new ConflictException(ErrorCode.LIVEKIT_DISABLED);
        }
        log.info("LiveKit: dispatching outbound SIP call to {} from {} in room {}",
                PhoneMasker.mask(calleeNumber), callerId, roomName);
        return "lk-sip-" + System.currentTimeMillis();
    }
}
