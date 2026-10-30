package uz.murodjon.robotcallv2.callrecord.application.service;

import org.springframework.stereotype.Service;
import uz.murodjon.robotcallv2.agent.ari.AriService;
import uz.murodjon.robotcallv2.agent.ari.WebTestProperties;
import uz.murodjon.robotcallv2.agent.queue.OperatorCallControl;
import uz.murodjon.robotcallv2.agent.queue.OperatorFirstDialer;
import uz.murodjon.robotcallv2.agent.telephony.SupervisorAccess;
import uz.murodjon.robotcallv2.agent.telephony.WebRtcJoin;
import uz.murodjon.robotcallv2.audit.application.port.input.AuditUseCase;
import uz.murodjon.robotcallv2.callrecord.application.dto.*;
import uz.murodjon.robotcallv2.callrecord.application.port.input.CallControlUseCase;
import uz.murodjon.robotcallv2.callrecord.domain.entity.CallTransferTarget;
import uz.murodjon.robotcallv2.operator.application.dto.OperatorJoinResponse;
import uz.murodjon.robotcallv2.scenario.application.port.input.ScenarioUseCase;
import uz.murodjon.robotcallv2.scenario.domain.entity.ScenarioDefinition;
import uz.murodjon.robotcallv2.security.CurrentUser;
import uz.murodjon.robotcallv2.shared.api.AudioStreamWriter;
import uz.murodjon.robotcallv2.shared.exception.ConflictException;
import uz.murodjon.robotcallv2.shared.exception.ErrorCode;
import uz.murodjon.robotcallv2.shared.exception.ForbiddenException;
import uz.murodjon.robotcallv2.shared.exception.ValidationException;

import java.util.List;

/**
 * The manual-call API's own service ({@code /api/calls}) — the operator actions for
 * placing a call by hand, watching what is live, and making a channel play or say
 * something.
 */
@Service
public class CallService implements CallControlUseCase {

    /**
     * SIP header the browser sends its session id in; the dialplan reads it into the Stasis
     * arguments (asterisk/etc/asterisk/extensions.conf, extension 700).
     */
    private static final String WEB_TEST_SESSION_HEADER = "X-Web-Test";

    private final AriService ariService;
    private final ScenarioUseCase scenarioUseCase;
    private final AuditUseCase auditUseCase;
    private final WebTestProperties webTestProperties;
    private final OperatorCallControl operatorCallControl;
    private final OperatorFirstDialer operatorFirstDialer;
    private final CurrentUser currentUser;

    public CallService(AriService ariService, ScenarioUseCase scenarioUseCase, AuditUseCase auditUseCase,
                       WebTestProperties webTestProperties, OperatorCallControl operatorCallControl,
                       OperatorFirstDialer operatorFirstDialer,
                       CurrentUser currentUser) {
        this.ariService = ariService;
        this.scenarioUseCase = scenarioUseCase;
        this.auditUseCase = auditUseCase;
        this.webTestProperties = webTestProperties;
        this.operatorCallControl = operatorCallControl;
        this.operatorFirstDialer = operatorFirstDialer;
        this.currentUser = currentUser;
    }

    @Override
    public List<LiveCallRow> findLiveCalls(long companyId) {
        return ariService.findLiveCalls(companyId);
    }

    /**
     * Every per-channel action starts here: a channel that is not a live call of the
     * caller's company does not exist as far as that caller is concerned, whether it is
     * another tenant's or already gone.
     */
    private void requireOwned(long companyId, String channelId) {
        if (!ariService.isActiveCallOf(companyId, channelId)) {
            throw new ConflictException(ErrorCode.CALL_NOT_ACTIVE, channelId);
        }
    }

    @Override
    public CallOriginateResponse originate(long companyId, String number, Long scenarioId, Long agentId, Long sipTrunkId) {
        if (scenarioId != null && agentId != null) {
            throw new ValidationException(ErrorCode.CALL_SOURCE_INVALID);
        }
        return ariService.originateManualCall(companyId, number, scenarioId, agentId, sipTrunkId);
    }

    @Override
    public CallOriginateResponse originateTestCall(long companyId, String number, ScenarioDefinition definition,
                                                   Long sipTrunkId) {
        requireValid(definition);
        return ariService.originateTestCall(companyId, number, definition, sipTrunkId);
    }

    @Override
    public WebTestCallResponse startWebTest(long companyId, WebTestCallRequest request) {
        boolean inRoom = ariService.usesBrowserRooms(companyId);
        if (!inRoom && (webTestProperties.wsUrl() == null || webTestProperties.wsUrl().isBlank())) {
            throw new ConflictException(ErrorCode.WEB_TEST_NOT_CONFIGURED);
        }
        int sources = (request.campaignId() != null ? 1 : 0) + (request.aiAgentId() != null ? 1 : 0)
                + (request.scenarioId() != null ? 1 : 0) + (request.definition() != null ? 1 : 0);
        if (sources > 1 || (request.targetId() != null && request.campaignId() == null)
                || (request.agentVersion() != null && request.campaignId() == null && request.aiAgentId() == null)) {
            throw new ValidationException(ErrorCode.WEB_TEST_SOURCE_INVALID);
        }
        if (request.definition() != null) {
            requireValid(request.definition());
        }
        String sessionId = ariService.prepareWebTest(companyId, request.campaignId(), request.targetId(),
                request.aiAgentId(), request.agentVersion(), request.scenarioId(), request.definition());
        if (inRoom) {
            WebRtcJoin join = ariService.openBrowserCall(companyId, sessionId);
            return new WebTestCallResponse(sessionId, null, null, null, null, null,
                    new OperatorJoinResponse(join.url(), join.token(), join.identity()));
        }
        return new WebTestCallResponse(sessionId, webTestProperties.wsUrl(), webTestProperties.sipUser(),
                webTestProperties.sipPassword(), webTestProperties.dialNumber(), WEB_TEST_SESSION_HEADER, null);
    }

    private void requireValid(ScenarioDefinition definition) {
        var result = scenarioUseCase.validate(definition);
        if (!result.valid()) {
            throw ValidationException.withErrors(ErrorCode.SCENARIO_DEFINITION_INVALID, result.errors(), result.errors().size());
        }
    }

    @Override
    public PlayResponse play(long companyId, String channelId, String file) {
        requireOwned(companyId, channelId);
        return ariService.playRecording(channelId, file);
    }

    @Override
    public SayResponse say(long companyId, String channelId, String text, String language, String voice) {
        requireOwned(companyId, channelId);
        return ariService.say(channelId, text, language, voice);
    }

    @Override
    public HangupResponse hangup(long companyId, String channelId) {
        requireOwned(companyId, channelId);
        ariService.hangupChannel(channelId);
        auditUseCase.record(companyId, "CALL_HANGUP_MANUAL", "call", channelId, null);
        return new HangupResponse(channelId, "HUNG_UP");
    }

    @Override
    public TransferResponse transfer(long companyId, String channelId, TransferCallRequest request) {
        if (request == null || request.type() == null) {
            requireOwned(companyId, channelId);
            ariService.transferToOperator(channelId);
            auditUseCase.record(companyId, "CALL_TRANSFER_MANUAL", "call", channelId, null);
            return new TransferResponse(channelId, "TRANSFERRING");
        }
        operatorCallControl.transfer(companyId, channelId, toTarget(request));
        auditUseCase.record(companyId, "CALL_TRANSFER", "call", channelId, request.type().name());
        return new TransferResponse(channelId, "TRANSFERRING");
    }

    @Override
    public TransferResponse hold(long companyId, String channelId) {
        operatorCallControl.hold(companyId, channelId);
        return new TransferResponse(channelId, "ON_HOLD");
    }

    @Override
    public TransferResponse unhold(long companyId, String channelId) {
        operatorCallControl.unhold(companyId, channelId);
        return new TransferResponse(channelId, "RESUMED");
    }

    @Override
    public TransferResponse startConsult(long companyId, String channelId, TransferCallRequest request) {
        if (request == null || request.type() == null) {
            throw new ValidationException(ErrorCode.CALL_TRANSFER_TYPE_REQUIRED);
        }
        operatorCallControl.startConsult(companyId, channelId, toTarget(request));
        auditUseCase.record(companyId, "CALL_CONSULT", "call", channelId, request.type().name());
        return new TransferResponse(channelId, "CONSULTING");
    }

    @Override
    public TransferResponse completeConsult(long companyId, String channelId) {
        operatorCallControl.completeConsult(companyId, channelId);
        auditUseCase.record(companyId, "CALL_CONSULT_COMPLETE", "call", channelId, null);
        return new TransferResponse(channelId, "TRANSFERRED");
    }

    @Override
    public TransferResponse cancelConsult(long companyId, String channelId) {
        operatorCallControl.cancelConsult(companyId, channelId);
        return new TransferResponse(channelId, "RESUMED");
    }

    @Override
    public MonitorCallResponse monitor(long companyId, String channelId, MonitorCallRequest request) {
        SupervisorAccess access = ariService.monitorCall(companyId, requireUserId(), channelId, request.mode());
        auditUseCase.record(companyId, "CALL_MONITOR", "call", channelId, request.mode().name());
        WebRtcJoin join = access.join();
        return new MonitorCallResponse(channelId, access.channelId(), request.mode(),
                join != null ? new OperatorJoinResponse(join.url(), join.token(), join.identity()) : null);
    }

    @Override
    public ClickToCallResponse clickToCall(long companyId, ClickToCallRequest request) {
        String operatorChannel = operatorFirstDialer.dial(companyId, requireUserId(), request.phone(), null, false);
        auditUseCase.record(companyId, "CALL_CLICK_TO_CALL", "call", operatorChannel, request.phone());
        return new ClickToCallResponse(operatorChannel, request.phone());
    }

    private long requireUserId() {
        return currentUser.id().orElseThrow(() -> new ForbiddenException(ErrorCode.NO_USER_SESSION));
    }

    private static CallTransferTarget toTarget(TransferCallRequest request) {
        return new CallTransferTarget(request.type(), request.targetId(), request.number());
    }

    @Override
    public AudioStreamWriter listen(long companyId, String channelId) {
        requireOwned(companyId, channelId);
        AudioStreamWriter body = ariService.listen(channelId);
        auditUseCase.record(companyId, "CALL_LISTEN", "call", channelId, null);
        return body;
    }
}
