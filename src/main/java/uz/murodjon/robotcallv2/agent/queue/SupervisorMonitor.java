package uz.murodjon.robotcallv2.agent.queue;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Component;
import uz.murodjon.robotcallv2.agent.ari.AriService;
import uz.murodjon.robotcallv2.agent.flow.FlowCall;
import uz.murodjon.robotcallv2.callrecord.application.port.input.CallEventUseCase;
import uz.murodjon.robotcallv2.callrecord.domain.enums.CallEventType;
import uz.murodjon.robotcallv2.callrecord.domain.enums.MonitorMode;
import uz.murodjon.robotcallv2.operator.application.port.input.OperatorAccountUseCase;
import uz.murodjon.robotcallv2.operator.domain.entity.OperatorSipAccount;
import uz.murodjon.robotcallv2.shared.exception.ConflictException;
import uz.murodjon.robotcallv2.shared.exception.ErrorCode;
import uz.murodjon.robotcallv2.shared.exception.NotFoundException;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * A supervisor joining someone else's call (docs/INBOUND-PBX-PLAN.md §10): their own phone
 * rings, and once answered it is put on the call as a listener (an Asterisk snoop on the
 * caller, both directions), a coach (a snoop on the operator that whispers into their ear
 * only), or a third party in the call's own bridge.
 */
@Component
public class SupervisorMonitor {

    private static final Logger log = LoggerFactory.getLogger(SupervisorMonitor.class);
    private static final int RING_SEC = 30;

    private final ObjectProvider<AriService> ariProvider;
    private final OperatorAccountUseCase operatorAccountUseCase;
    private final CallEventUseCase callEventUseCase;

    private static final class Session {
        private final String token;
        private final String callerChannelId;
        private final MonitorMode mode;
        private volatile String supervisorChannelId;
        private volatile String snoopChannelId;
        private volatile String bridgeId;

        private Session(String token, String callerChannelId, MonitorMode mode) {
            this.token = token;
            this.callerChannelId = callerChannelId;
            this.mode = mode;
        }
    }

    private final Map<String, Session> byToken = new ConcurrentHashMap<>();
    private final Map<String, Session> bySupervisorChannel = new ConcurrentHashMap<>();

    public SupervisorMonitor(ObjectProvider<AriService> ariProvider, OperatorAccountUseCase operatorAccountUseCase,
                             CallEventUseCase callEventUseCase) {
        this.ariProvider = ariProvider;
        this.operatorAccountUseCase = operatorAccountUseCase;
        this.callEventUseCase = callEventUseCase;
    }

    /** Rings the supervisor's phone; returns its channel. */
    public String start(long companyId, long supervisorUserId, String callerChannelId, MonitorMode mode) {
        AriService ari = ariProvider.getObject();
        if (!ari.isActiveCallOf(companyId, callerChannelId)) {
            throw new ConflictException(ErrorCode.CALL_NOT_ACTIVE, callerChannelId);
        }
        FlowCall call = ari.findFlowCall(callerChannelId);
        if (mode == MonitorMode.WHISPER && (call == null || call.operatorChannelId() == null)) {
            throw new ConflictException(ErrorCode.CALL_NOT_WITH_OPERATOR, callerChannelId);
        }
        OperatorSipAccount account = operatorAccountUseCase.findSipAccount(companyId, supervisorUserId);
        if (account == null) {
            throw new NotFoundException(ErrorCode.OPERATOR_SIP_ACCOUNT_NOT_FOUND, supervisorUserId);
        }
        Session session = new Session(UUID.randomUUID().toString(), callerChannelId, mode);
        byToken.put(session.token, session);
        String channelId = ari.originateToStasis("PJSIP/" + account.endpointName(), "monitor," + session.token,
                RING_SEC, "\"Monitoring\" <" + mode.name().toLowerCase() + ">");
        session.supervisorChannelId = channelId;
        bySupervisorChannel.put(channelId, session);
        callEventUseCase.record(companyId, ari.callAttemptIdOf(callerChannelId), CallEventType.MONITOR,
                "userId", supervisorUserId, "mode", mode.name());
        return channelId;
    }

    public void onSupervisorAnswered(String token, String channelId) {
        Session session = byToken.get(token);
        AriService ari = ariProvider.getObject();
        if (session == null) {
            ari.hangupChannel(channelId);
            return;
        }
        try {
            ari.answerChannel(channelId);
            switch (session.mode) {
                case BARGE -> ari.addToBridge(ari.bridgeOf(session.callerChannelId), channelId);
                case LISTEN, WHISPER -> {
                    FlowCall call = ari.findFlowCall(session.callerChannelId);
                    String target = session.mode == MonitorMode.WHISPER && call != null && call.operatorChannelId() != null
                            ? call.operatorChannelId() : session.callerChannelId;
                    session.snoopChannelId = ari.snoop(target, "both",
                            session.mode == MonitorMode.WHISPER ? "out" : "none");
                    session.bridgeId = ari.createBridge();
                    ari.addToBridge(session.bridgeId, session.snoopChannelId);
                    ari.addToBridge(session.bridgeId, channelId);
                }
            }
        } catch (Exception e) {
            log.warn("Monitoring {} failed: {}", session.callerChannelId, e.getMessage());
            ari.hangupChannel(channelId);
        }
    }

    /** A supervisor left (clean up), or a watched call ended (the supervisor goes too). */
    public void onChannelGone(String channelId) {
        Session session = bySupervisorChannel.remove(channelId);
        AriService ari = ariProvider.getObject();
        if (session != null) {
            byToken.remove(session.token);
            if (session.snoopChannelId != null) {
                ari.hangupChannel(session.snoopChannelId);
            }
            if (session.bridgeId != null) {
                ari.destroyBridge(session.bridgeId);
            }
            return;
        }
        for (Session watching : bySupervisorChannel.values()) {
            if (watching.callerChannelId.equals(channelId) && watching.supervisorChannelId != null) {
                ari.hangupChannel(watching.supervisorChannelId);
            }
        }
    }
}
