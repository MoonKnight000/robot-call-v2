package uz.murodjon.robotcallv2.agent.ari;

import ch.loway.oss.ari4java.ARI;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Component;
import uz.murodjon.robotcallv2.agent.queue.SupervisorMonitor;
import uz.murodjon.robotcallv2.agent.session.CallSession;
import uz.murodjon.robotcallv2.agent.telephony.OutboundDial;
import uz.murodjon.robotcallv2.agent.telephony.SupervisorAccess;
import uz.murodjon.robotcallv2.agent.telephony.TelephonyGateway;
import uz.murodjon.robotcallv2.agent.telephony.WebRtcJoin;
import uz.murodjon.robotcallv2.callrecord.application.port.input.CallAttemptUseCase;
import uz.murodjon.robotcallv2.callrecord.domain.enums.MonitorMode;
import uz.murodjon.robotcallv2.dialer.application.dto.OutboundCall;
import uz.murodjon.robotcallv2.dialer.application.port.input.OutboundCallTrackingUseCase;
import uz.murodjon.robotcallv2.shared.dialog.Disposition;
import uz.murodjon.robotcallv2.shared.dialog.TelephonyEngine;
import uz.murodjon.robotcallv2.shared.exception.ErrorCode;
import uz.murodjon.robotcallv2.shared.exception.ExternalServiceException;
import uz.murodjon.robotcallv2.shared.util.PhoneMasker;
import uz.murodjon.robotcallv2.siptrunk.application.dto.SipTrunkRow;

import java.util.Collections;
import java.util.List;

/** The Asterisk engine's {@link TelephonyGateway}: call control and dialling over ARI. */
@Component
public class AsteriskGateway implements TelephonyGateway {

    private static final Logger log = LoggerFactory.getLogger(AsteriskGateway.class);

    private final AriClient ariClient;
    private final AsteriskProperties asteriskProperties;
    private final OutboundRouting outboundRouting;
    private final CallAttemptUseCase callAttemptUseCase;
    private final OutboundCallTrackingUseCase outboundCallTrackingUseCase;
    private final SupervisorMonitor supervisorMonitor;
    private final ObjectProvider<OperatorTransferService> operatorTransferServiceProvider;

    AsteriskGateway(AriClient ariClient,
                    AsteriskProperties asteriskProperties,
                    OutboundRouting outboundRouting,
                    CallAttemptUseCase callAttemptUseCase,
                    OutboundCallTrackingUseCase outboundCallTrackingUseCase,
                    SupervisorMonitor supervisorMonitor,
                    ObjectProvider<OperatorTransferService> operatorTransferServiceProvider) {
        this.operatorTransferServiceProvider = operatorTransferServiceProvider;
        this.supervisorMonitor = supervisorMonitor;
        this.ariClient = ariClient;
        this.asteriskProperties = asteriskProperties;
        this.outboundRouting = outboundRouting;
        this.callAttemptUseCase = callAttemptUseCase;
        this.outboundCallTrackingUseCase = outboundCallTrackingUseCase;
    }

    @Override
    public TelephonyEngine findEngine() {
        return TelephonyEngine.ASTERISK;
    }

    @Override
    public String originate(OutboundDial dial) {
        String number = dial.number();
        OutboundCall outboundCall = dial.outboundCall();
        List<SipTrunkRow> candidateTrunks;
        try {
            candidateTrunks = outboundRouting.isLocalNumber(number) ? Collections.singletonList(null)
                    : outboundRouting.findCandidateTrunks(dial.companyId(), dial.sipTrunkId());
        } catch (RuntimeException e) {
            // Refused before Asterisk was asked, but the dialer has already counted the
            // attempt: it still leaves a row saying why.
            callAttemptUseCase.recordUnplacedAttempt(dial.companyId(), dial.targetId(), number, dial.language(),
                    Disposition.FAILED, "no trunk for the call: " + e.getMessage());
            throw e;
        }

        Exception lastException = null;
        String lastEndpoint = null;

        for (int i = 0; i < candidateTrunks.size(); i++) {
            SipTrunkRow trunk = candidateTrunks.get(i);
            String endpoint = outboundRouting.findEndpoint(number, trunk);
            lastEndpoint = endpoint;
            String callerId = outboundRouting.findCallerId(trunk, outboundCall);
            String dialNumber = !outboundRouting.isLocalNumber(number) && number.startsWith("+")
                    ? number.substring(1)
                    : number;
            outboundRouting.awaitTrunkSlot(number, endpoint, trunk);
            try {
                // Inside the try on purpose: an ARI connection that is down is one more way
                // for the call never to happen, and it has to leave a row like the rest.
                var request = ariClient.requireConnection().channels()
                        .originate("PJSIP/" + dialNumber + "@" + endpoint)
                        .setApp(asteriskProperties.appName())
                        .setAppArgs(dial.callId())
                        .setTimeout(asteriskProperties.answerTimeoutSec());
                if (callerId != null && !callerId.isBlank()) {
                    request.setCallerId(callerId);
                }
                String channelId = request.execute().getId();
                callAttemptUseCase.startAttempt(dial.companyId(), dial.targetId(), channelId, number, dial.language(),
                        null, outboundCall != null ? outboundCall.variantId() : null);
                if (outboundCall != null) {
                    outboundCallTrackingUseCase.register(channelId, outboundCall);
                }
                Long usedTrunkId = trunk != null ? trunk.id() : dial.sipTrunkId();
                log.info("Originated call {} to {} (dial={}) via {} (trunkId={}) -> channel {}",
                        dial.callId(), PhoneMasker.mask(number), PhoneMasker.mask(dialNumber), endpoint, usedTrunkId,
                        channelId);
                return channelId;
            } catch (Exception e) {
                lastException = e;
                if (i < candidateTrunks.size() - 1) {
                    log.warn("Originate via trunk {} ({}) failed ({}); attempting failover to next trunk...",
                            trunk != null ? trunk.name() : "default", endpoint, e.getMessage());
                }
            }
        }

        // Asterisk never took the call, so no channel and no ChannelDestroyed will
        // ever arrive to close a row — write the closed row here instead, or the
        // attempt the dialer already counted leaves no trace of why it failed.
        callAttemptUseCase.recordUnplacedAttempt(dial.companyId(), dial.targetId(), number, dial.language(),
                Disposition.FAILED, "originate via " + lastEndpoint + " failed: "
                        + (lastException != null ? lastException.getMessage() : "unknown"));
        throw new ExternalServiceException(ErrorCode.ARI_ORIGINATE_FAILED, "asterisk", lastException,
                number, lastEndpoint, lastException != null ? lastException.getMessage() : "unknown");
    }

    @Override
    public void transferToOperator(String callId) {
        operatorTransferServiceProvider.getObject().transferToOperator(callId);
    }

    @Override
    public WebRtcJoin openBrowserCall(String sessionId) {
        return null;
    }

    @Override
    public SupervisorAccess monitor(long companyId, long supervisorUserId, String callId, MonitorMode mode) {
        return SupervisorAccess.ringing(supervisorMonitor.start(companyId, supervisorUserId, callId, mode));
    }

    @Override
    public void answer(String callId) {
        ariClient.answer(callId);
    }

    @Override
    public void hangup(String callId) {
        ariClient.hangup(callId);
    }

    @Override
    public void hangupBusy(String callId) {
        ariClient.hangupBusy(callId);
    }

    @Override
    public void ring(String callId) {
        ariClient.ring(callId);
    }

    @Override
    public void sendDtmf(String callId, String digits) {
        ariClient.sendDtmf(callId, digits);
    }

    @Override
    public void releaseMedia(CallSession session) {
        ARI current = ariClient.findConnection();
        if (current == null) {
            return;
        }
        try {
            current.channels().hangup(session.extMediaChannelId()).execute();
        } catch (Exception e) {
            log.debug("externalMedia channel {} already gone: {}", session.extMediaChannelId(), e.getMessage());
        }
        try {
            current.bridges().destroy(session.bridgeId()).execute();
        } catch (Exception e) {
            log.debug("Bridge {} already gone: {}", session.bridgeId(), e.getMessage());
        }
    }
}
