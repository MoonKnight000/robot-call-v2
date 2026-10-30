package uz.murodjon.robotcallv2.agent.ari;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Component;
import uz.murodjon.robotcallv2.agent.livekit.LiveKitBridgeClient;
import uz.murodjon.robotcallv2.agent.livekit.LiveKitRoute;
import uz.murodjon.robotcallv2.agent.livekit.LiveKitTelephonyService;
import uz.murodjon.robotcallv2.agent.session.CallSession;
import uz.murodjon.robotcallv2.agent.telephony.DtmfDigits;
import uz.murodjon.robotcallv2.agent.telephony.OutboundDial;
import uz.murodjon.robotcallv2.agent.telephony.SupervisorAccess;
import uz.murodjon.robotcallv2.agent.telephony.TelephonyGateway;
import uz.murodjon.robotcallv2.agent.telephony.WebRtcJoin;
import uz.murodjon.robotcallv2.callrecord.application.port.input.CallAttemptUseCase;
import uz.murodjon.robotcallv2.callrecord.application.port.input.CallEventUseCase;
import uz.murodjon.robotcallv2.callrecord.domain.enums.CallEventType;
import uz.murodjon.robotcallv2.callrecord.domain.enums.MonitorMode;
import uz.murodjon.robotcallv2.dialer.application.dto.OutboundCall;
import uz.murodjon.robotcallv2.dialer.application.port.input.OutboundCallTrackingUseCase;
import uz.murodjon.robotcallv2.shared.dialog.Disposition;
import uz.murodjon.robotcallv2.shared.exception.ConflictException;
import uz.murodjon.robotcallv2.shared.exception.ErrorCode;
import uz.murodjon.robotcallv2.shared.dialog.TelephonyEngine;
import uz.murodjon.robotcallv2.shared.util.PhoneMasker;
import uz.murodjon.robotcallv2.siptrunk.application.dto.SipTrunkRow;
import uz.murodjon.robotcallv2.siptrunk.application.port.input.SipTrunkUseCase;
import uz.murodjon.robotcallv2.siptrunk.domain.entity.SipTrunkCredentials;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * The LiveKit engine's {@link TelephonyGateway}. A call is a room: its phone leg is already a
 * participant when the pipeline sees it, so there is nothing to answer or ring, and closing the
 * room ends the call. Lives beside {@link LiveKitCallService}, whose call lifecycle it starts and which
 * rests on this package's internals.
 */
@Component
public class LiveKitGateway implements TelephonyGateway {

    private static final Logger log = LoggerFactory.getLogger(LiveKitGateway.class);

    private final LiveKitTelephonyService liveKitTelephonyService;
    private final LiveKitBridgeClient liveKitBridgeClient;
    private final OutboundRouting outboundRouting;
    private final SipTrunkUseCase sipTrunkUseCase;
    private final CallAttemptUseCase callAttemptUseCase;
    private final OutboundCallTrackingUseCase outboundCallTrackingUseCase;
    private final ActiveCallService activeCallService;
    private final CallSessionRegistry callSessionRegistry;
    private final CallEventUseCase callEventUseCase;
    /** Lazy: the LiveKit call lifecycle reaches the teardown, which reaches the gateways. */
    private final ObjectProvider<LiveKitCallService> liveKitCallServiceProvider;

    LiveKitGateway(LiveKitTelephonyService liveKitTelephonyService,
                   LiveKitBridgeClient liveKitBridgeClient,
                   OutboundRouting outboundRouting,
                   SipTrunkUseCase sipTrunkUseCase,
                   CallAttemptUseCase callAttemptUseCase,
                   OutboundCallTrackingUseCase outboundCallTrackingUseCase,
                   ActiveCallService activeCallService,
                   CallSessionRegistry callSessionRegistry,
                   CallEventUseCase callEventUseCase,
                   ObjectProvider<LiveKitCallService> liveKitCallServiceProvider) {
        this.liveKitTelephonyService = liveKitTelephonyService;
        this.liveKitBridgeClient = liveKitBridgeClient;
        this.outboundRouting = outboundRouting;
        this.sipTrunkUseCase = sipTrunkUseCase;
        this.callAttemptUseCase = callAttemptUseCase;
        this.outboundCallTrackingUseCase = outboundCallTrackingUseCase;
        this.activeCallService = activeCallService;
        this.callSessionRegistry = callSessionRegistry;
        this.callEventUseCase = callEventUseCase;
        this.liveKitCallServiceProvider = liveKitCallServiceProvider;
    }

    @Override
    public TelephonyEngine findEngine() {
        return TelephonyEngine.LIVEKIT;
    }

    @Override
    public boolean owns(String callId) {
        return LiveKitTelephonyService.isLiveKitRoom(callId);
    }

    /**
     * One id names the attempt row, the tracking entry and the room, so whatever ends the call
     * finds all three; a room that cannot be opened leaves a closed row, as an Asterisk originate
     * that fails does. Returns once the room is open — the ringing runs on a call thread
     * ({@link LiveKitCallService#dialOutbound}), as an Asterisk originate returns before the callee answers.
     * A manual or test call ({@code outboundCall} null) is tracked by the caller in
     * {@link PendingCallRegistry}, as on Asterisk.
     */
    @Override
    public String originate(OutboundDial dial) {
        String channelId = LiveKitTelephonyService.OUTBOUND_ROOM_PREFIX + dial.callId();
        OutboundCall outboundCall = dial.outboundCall();
        List<LiveKitRoute> routes;
        try {
            routes = findRoutes(dial, outboundCall);
            liveKitTelephonyService.createCallRoom(channelId);
        } catch (RuntimeException e) {
            callAttemptUseCase.recordUnplacedAttempt(dial.companyId(), dial.targetId(), dial.number(), dial.language(),
                    Disposition.FAILED, "LiveKit dispatch failed: " + e.getMessage());
            throw e;
        }
        callAttemptUseCase.startAttempt(dial.companyId(), dial.targetId(), channelId, dial.number(), dial.language(),
                null, outboundCall != null ? outboundCall.variantId() : null);
        if (outboundCall != null) {
            outboundCallTrackingUseCase.register(channelId, outboundCall);
        }
        liveKitCallServiceProvider.getObject().dialOutbound(channelId, dial.number(), routes, outboundCall);
        log.info("Originated LiveKit call {} to {} ({} trunk(s) to try) -> room/channel {}",
                dial.callId(), PhoneMasker.mask(dial.number()), routes.size(), channelId);
        return channelId;
    }

    /**
     * The trunks to ring over, in order. The first must be usable — a trunk LiveKit cannot be
     * given (not managed) refuses the call; the ones after it are failover and are skipped when
     * they are not usable.
     */
    private List<LiveKitRoute> findRoutes(OutboundDial dial, OutboundCall outboundCall) {
        List<SipTrunkRow> trunks = outboundRouting.isLocalNumber(dial.number()) ? Collections.singletonList(null)
                : outboundRouting.findCandidateTrunks(dial.companyId(), dial.sipTrunkId());
        List<LiveKitRoute> routes = new ArrayList<>();
        for (int i = 0; i < trunks.size(); i++) {
            SipTrunkRow trunk = trunks.get(i);
            try {
                // The company's own trunk goes to LiveKit with the call; no trunk here means the
                // platform company, which dials the platform's LiveKit trunk (LIVEKIT_SIP_TRUNK_ID).
                SipTrunkCredentials credentials = trunk != null
                        ? sipTrunkUseCase.requireCredentialsForCall(dial.companyId(), trunk.id()) : null;
                routes.add(new LiveKitRoute(credentials,
                        () -> outboundRouting.findCallerId(trunk, outboundCall, dial.companyId())));
            } catch (RuntimeException e) {
                if (i == 0) {
                    throw e;
                }
                log.debug("Trunk {} is not available for LiveKit failover: {}", trunk.id(), e.getMessage());
            }
        }
        return routes;
    }

    /**
     * The supervisor watches from the browser: hidden and silent when listening, speaking into the
     * call when barging in. Whispering to the operator alone cannot be done here — everyone in the
     * room hears every voice in it — so it is refused rather than overheard.
     */
    @Override
    public SupervisorAccess monitor(long companyId, long supervisorUserId, String callId, MonitorMode mode) {
        if (mode == MonitorMode.WHISPER) {
            throw new ConflictException(ErrorCode.LIVEKIT_MONITOR_MODE_UNSUPPORTED, mode);
        }
        if (!activeCallService.isActiveCallOf(companyId, callId)) {
            throw new ConflictException(ErrorCode.CALL_NOT_ACTIVE, callId);
        }
        WebRtcJoin join = liveKitTelephonyService.createSupervisorJoin(callId, supervisorUserId,
                mode == MonitorMode.BARGE);
        callEventUseCase.record(companyId, callSessionRegistry.find(callId).callAttemptId(), CallEventType.MONITOR,
                "userId", supervisorUserId, "mode", mode.name());
        return SupervisorAccess.browser(join);
    }

    @Override
    public void transferToOperator(String callId) {
        liveKitCallServiceProvider.getObject().transferCall(callId);
    }

    @Override
    public WebRtcJoin openBrowserCall(String sessionId) {
        return liveKitTelephonyService.createBrowserCallJoin(sessionId);
    }

    @Override
    public void answer(String callId) {
        // The SIP participant joined the room already answered.
    }

    @Override
    public void hangup(String callId) {
        liveKitTelephonyService.hangUp(callId);
    }

    @Override
    public void hangupBusy(String callId) {
        liveKitTelephonyService.hangUp(callId);
    }

    @Override
    public void ring(String callId) {
        // A room has no ringback to give.
    }

    @Override
    public void sendDtmf(String callId, String digits) {
        String cleaned = DtmfDigits.clean(digits);
        if (cleaned.isEmpty()) {
            log.warn("[{}] refusing to send DTMF: '{}' has no dialable digits", callId, digits);
            return;
        }
        liveKitBridgeClient.sendDtmf(callId, cleaned);
        log.info("[{}] sent DTMF '{}'", callId, cleaned);
    }

    @Override
    public void releaseMedia(CallSession session) {
        // Closing the room takes the media bridge, which would otherwise hold it open, out with it.
        liveKitTelephonyService.hangUp(session.channelId());
    }
}
