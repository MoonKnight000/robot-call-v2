package uz.murodjon.robotcallv2.agent.ari;

import ch.loway.oss.ari4java.ARI;
import ch.loway.oss.ari4java.AriVersion;
import ch.loway.oss.ari4java.generated.AriWSHelper;
import ch.loway.oss.ari4java.generated.models.Endpoint;
import ch.loway.oss.ari4java.tools.BaseAriAction;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import uz.murodjon.robotcallv2.shared.exception.ErrorCode;
import uz.murodjon.robotcallv2.shared.exception.ExternalServiceException;

import java.util.List;
import java.util.concurrent.ExecutorService;

/**
 * The ARI connection and the plain REST calls made over it: answer, hang up, ring, bridges,
 * snoops, originates into Stasis, DTMF. The one holder of the {@link ARI} handle — everything
 * else asks {@link #requireConnection()} (or {@link #findConnection()} where a missing
 * connection is not an error).
 */
@Component
class AriClient {

    private static final Logger log = LoggerFactory.getLogger(AriClient.class);

    /** Tone length in ms. Carrier IVRs stop recognising anything much shorter than this. */
    private static final int DTMF_TONE_MS = 100;
    /** Silence between tones in ms — without a gap a repeated digit reads as one long press. */
    private static final int DTMF_GAP_MS = 100;
    /** An IVR selection is a digit or two; a longer string is a model that has lost the plot. */
    private static final int MAX_DTMF_DIGITS = 12;

    private final AsteriskProperties asteriskProperties;
    private final ExecutorService callExecutor;

    private volatile ARI ari;

    AriClient(AsteriskProperties asteriskProperties, ExecutorService callExecutor) {
        this.asteriskProperties = asteriskProperties;
        this.callExecutor = callExecutor;
    }

    /**
     * Builds the connection and opens its event websocket, delivering every event to
     * {@code eventHandler}.
     */
    synchronized void open(AriWSHelper eventHandler) throws Exception {
        // Asterisk sends event fields this pinned ari4java's generated models do not
        // declare (ChannelDestroyed.tech_cause). Less strict = the handler skips the
        // field and logs the would-be exception at WARN, so the event IS delivered and
        // handled and only the stack trace looks like a failure. That handler is
        // BaseAriAction's only log statement, and AriWSHelper likewise warns "Event
        // <X> Unhandled" for every ARI event this app deliberately does not override
        // (Dial, ChannelVarset, ChannelEnteredBridge, ...) — a call produces dozens,
        // which buries this application's own lines. Both are therefore pinned to
        // ERROR in application.yml; their genuine failures ("Unknown Event", decode
        // errors) are logged at ERROR and survive that.
        BaseAriAction.setObjectMapperLessStrict();
        ARI connection = ARI.build(asteriskProperties.ariUrl(), asteriskProperties.appName(), asteriskProperties.ariUser(), asteriskProperties.ariPassword(),
                AriVersion.IM_FEELING_LUCKY);
        // Assigned ahead of the websocket, so the first events find the connection to answer
        // with — and taken back when the websocket cannot open: a handle left behind then
        // read as connected, and ensureConnected never tried again.
        ari = connection;
        try {
            connection.events().eventWebsocket(asteriskProperties.appName()).execute(eventHandler);
        } catch (Exception e) {
            ari = null;
            try {
                connection.cleanup();
            } catch (Exception cleanupFailure) {
                log.debug("ARI cleanup after a failed connect: {}", cleanupFailure.getMessage());
            }
            throw e;
        }
    }

    boolean isConnected() {
        return ari != null;
    }

    ARI requireConnection() {
        ARI current = ari;
        if (current == null) {
            throw new ExternalServiceException(ErrorCode.ARI_NOT_CONNECTED, "asterisk");
        }
        return current;
    }

    /** The connection, or null when there is none — for cleanup that has nothing to do without one. */
    ARI findConnection() {
        return ari;
    }

    /** Forgets a connection that failed for good, so {@link AriEventDispatcher#ensureConnected} builds a new one. */
    void dropConnection() {
        ARI dead = ari;
        ari = null;
        if (dead != null) {
            callExecutor.execute(() -> {
                try {
                    dead.cleanup();
                } catch (Exception e) {
                    log.debug("ARI cleanup after a failure: {}", e.getMessage());
                }
            });
        }
    }

    /** Closes the connection at shutdown. */
    void cleanup() {
        ARI current = ari;
        if (current != null) {
            try {
                current.cleanup();
                log.info("ARI connection cleaned up");
            } catch (Exception e) {
                log.warn("ARI cleanup failed: {}", e.getMessage());
            }
        }
    }

    void answer(String channelId) {
        try {
            requireConnection().channels().answer(channelId).execute();
            log.info("Answered channel {}", channelId);
        } catch (Exception e) {
            log.warn("Answer failed for channel {}: {}", channelId, e.getMessage());
        }
    }

    void hangup(String channelId) {
        try {
            requireConnection().channels().hangup(channelId).execute();
            log.info("Hung up channel {}", channelId);
        } catch (Exception e) {
            log.warn("Hangup failed for channel {}: {}", channelId, e.getMessage());
        }
    }

    /** Refuses the channel with a busy signal instead of a plain hang-up. */
    void hangupBusy(String channelId) {
        try {
            requireConnection().channels().hangup(channelId).setReason("busy").execute();
        } catch (Exception e) {
            log.warn("Busy hangup failed for channel {}: {}", channelId, e.getMessage());
            hangup(channelId);
        }
    }

    /** Ringback to a channel that is waiting for someone to pick up. */
    void ring(String channelId) {
        try {
            requireConnection().channels().ring(channelId).execute();
        } catch (Exception e) {
            log.debug("Ring indication failed for {}: {}", channelId, e.getMessage());
        }
    }

    /** Whether a PJSIP endpoint has a registered, reachable contact — a softphone that is signed in. */
    boolean isEndpointOnline(String endpointName) {
        try {
            Endpoint endpoint = requireConnection().endpoints().get().setTech("PJSIP").setResource(endpointName).execute();
            return endpoint != null && "online".equalsIgnoreCase(endpoint.getState());
        } catch (Exception e) {
            return false;
        }
    }

    /** Every PJSIP endpoint with a registered, reachable contact — one request, not one per endpoint. */
    List<String> findOnlineEndpointNames() {
        try {
            List<Endpoint> endpoints = requireConnection().endpoints().listByTech().setTech("PJSIP").execute();
            return endpoints.stream()
                    .filter(endpoint -> "online".equalsIgnoreCase(endpoint.getState()))
                    .map(Endpoint::getResource)
                    .toList();
        } catch (Exception e) {
            throw new ExternalServiceException(ErrorCode.ARI_ENDPOINTS_UNAVAILABLE, "asterisk", e, e.getMessage());
        }
    }

    /**
     * A snoop on {@code channelId}: {@code spy} is which direction the snoop hears, {@code whisper}
     * which direction what is said into it goes ("out" = to that channel's own person).
     */
    String snoop(String channelId, String spy, String whisper) {
        try {
            return requireConnection().channels().snoopChannel(channelId, asteriskProperties.appName())
                    .setSpy(spy).setWhisper(whisper).setAppArgs("snoop").execute().getId();
        } catch (Exception e) {
            throw new ExternalServiceException(ErrorCode.ARI_SNOOP_FAILED, "asterisk", e, channelId, e.getMessage());
        }
    }

    String createBridge() {
        try {
            return requireConnection().bridges().create().setType("mixing").execute().getId();
        } catch (Exception e) {
            throw new ExternalServiceException(ErrorCode.ARI_BRIDGE_CREATE_FAILED, "asterisk", e, e.getMessage());
        }
    }

    void addToBridge(String bridgeId, String channelId) {
        try {
            requireConnection().bridges().addChannel(bridgeId, channelId).execute();
        } catch (Exception e) {
            throw new ExternalServiceException(ErrorCode.ARI_BRIDGE_ADD_FAILED, "asterisk", e, channelId, bridgeId,
                    e.getMessage());
        }
    }

    void removeFromBridge(String bridgeId, String channelId) {
        try {
            requireConnection().bridges().removeChannel(bridgeId, channelId).execute();
        } catch (Exception e) {
            throw new ExternalServiceException(ErrorCode.ARI_BRIDGE_REMOVE_FAILED, "asterisk", e, channelId, bridgeId,
                    e.getMessage());
        }
    }

    /** Best-effort: a bridge that is already gone is what was wanted anyway. */
    void destroyBridge(String bridgeId) {
        try {
            requireConnection().bridges().destroy(bridgeId).execute();
        } catch (Exception e) {
            log.debug("Bridge {} already gone: {}", bridgeId, e.getMessage());
        }
    }

    /** Originates {@code dial} into this application with {@code appArgs}; returns the new channel. */
    String originateToStasis(String dial, String appArgs, int timeoutSec, String callerId) {
        try {
            var request = requireConnection().channels()
                    .originate(dial)
                    .setApp(asteriskProperties.appName())
                    .setAppArgs(appArgs)
                    .setTimeout(timeoutSec);
            if (callerId != null && !callerId.isBlank()) {
                request.setCallerId(callerId);
            }
            return request.execute().getId();
        } catch (ExternalServiceException e) {
            throw e;
        } catch (Exception e) {
            throw new ExternalServiceException(ErrorCode.ARI_ORIGINATE_TO_STASIS_FAILED, "asterisk", e, dial, e.getMessage());
        }
    }

    /**
     * Sends keypad tones down an established call — the bot pressing buttons, not the client.
     *
     * <p>Outbound calls to a company reach an automated menu before they reach a person
     * ("buxgalteriya uchun 1 ni bosing"). Without this the call stops there: nobody is
     * listening for the digit and the menu times out. The agent hears the menu through the
     * same STT as any other speech and calls {@code sendDtmfTones} once it knows which
     * option matches (see {@code DialogTools}); this puts that digit on the line.
     *
     * <p>The timings are Asterisk's defaults made explicit: 100 ms per tone with 100 ms of
     * silence between, which is what carrier IVRs expect. Anything shorter is dropped by
     * some gateways.
     *
     * @param digits {@code 0-9}, {@code *}, {@code #} — anything else is rejected rather
     *               than sent, because a stray character makes Asterisk fail the whole request
     */
    void sendDtmf(String channelId, String digits) {
        String cleaned = cleanDtmf(digits);
        if (cleaned.isEmpty()) {
            log.warn("[{}] refusing to send DTMF: '{}' has no dialable digits", channelId, digits);
            return;
        }
        try {
            requireConnection().channels().sendDTMF(channelId)
                    .setDtmf(cleaned)
                    .setDuration(DTMF_TONE_MS)
                    .setBetween(DTMF_GAP_MS)
                    .execute();
            log.info("[{}] sent DTMF '{}'", channelId, cleaned);
        } catch (Exception e) {
            throw new ExternalServiceException(ErrorCode.ARI_DTMF_FAILED, "asterisk", e, cleaned, channelId);
        }
    }

    /**
     * Put Asterisk's jitter buffer on the caller's channel, before it joins the bridge, so
     * what it reads from the carrier reaches externalMedia evenly spaced. Best-effort: a
     * call without it is the call it was before.
     */
    void enableJitterBuffer(ARI current, String channelId) {
        String kind = asteriskProperties.jitterBuffer();
        if (kind == null || kind.isBlank()) {
            return;
        }
        try {
            current.channels().setChannelVar(channelId, "JITTERBUFFER(" + kind.trim() + ")")
                    .setValue("default")
                    .execute();
        } catch (Exception e) {
            log.warn("[{}] could not enable the {} jitter buffer: {}", channelId, kind, e.getMessage());
        }
    }

    /** Keeps only what a keypad can actually produce; everything else would fail the ARI request. */
    private static String cleanDtmf(String digits) {
        if (digits == null) {
            return "";
        }
        StringBuilder kept = new StringBuilder();
        for (char c : digits.toCharArray()) {
            if ((c >= '0' && c <= '9') || c == '*' || c == '#') {
                kept.append(c);
            }
        }
        return kept.length() > MAX_DTMF_DIGITS ? kept.substring(0, MAX_DTMF_DIGITS) : kept.toString();
    }
}
