package uz.murodjon.robotcallv2.callrecord.application.service;

import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody;
import uz.murodjon.robotcallv2.agent.ari.AriService;
import uz.murodjon.robotcallv2.audit.application.service.AuditService;
import uz.murodjon.robotcallv2.callrecord.application.dto.*;
import uz.murodjon.robotcallv2.callrecord.application.port.input.CallControlUseCase;
import uz.murodjon.robotcallv2.scenario.application.service.ScenarioService;
import uz.murodjon.robotcallv2.scenario.domain.entity.ScenarioDefinition;
import uz.murodjon.robotcallv2.shared.exception.ErrorCode;
import uz.murodjon.robotcallv2.shared.exception.ValidationException;

import java.util.List;

/**
 * The manual-call API's own service ({@code /api/calls}) — the operator actions for
 * placing a call by hand, watching what is live, and making a channel play or say
 * something.
 */
@Service
public class CallService implements CallControlUseCase {

    private final AriService ari;
    private final ScenarioService scenarios;
    private final AuditService audit;

    public CallService(AriService ari, ScenarioService scenarios, AuditService audit) {
        this.ari = ari;
        this.scenarios = scenarios;
        this.audit = audit;
    }

    @Override
    public List<LiveCallRow> liveCalls() {
        return ari.liveCalls();
    }

    @Override
    public CallOriginateResponse originate(String number, Long scenarioId) {
        return originate(number, scenarioId, null);
    }

    @Override
    public CallOriginateResponse originate(String number, Long scenarioId, Long sipTrunkId) {
        return ari.originateManualCall(number, scenarioId, sipTrunkId);
    }

    @Override
    public CallOriginateResponse originateTestCall(String number, ScenarioDefinition definition) {
        return originateTestCall(number, definition, null);
    }

    @Override
    public CallOriginateResponse originateTestCall(String number, ScenarioDefinition definition, Long sipTrunkId) {
        var result = scenarios.validate(definition);
        if (!result.valid()) {
            throw new ValidationException(ErrorCode.SCENARIO_DEFINITION_INVALID, String.join("; ", result.errors()));
        }
        return ari.originateTestCall(number, definition, sipTrunkId);
    }

    @Override
    public PlayResponse play(String channelId, String file) {
        return ari.playRecording(channelId, file);
    }

    @Override
    public SayResponse say(String channelId, String text, String language, String voice) {
        return ari.say(channelId, text, language, voice);
    }

    @Override
    public HangupResponse hangup(String channelId) {
        ari.hangupChannel(channelId);
        audit.record("CALL_HANGUP_MANUAL", "call", channelId, null);
        return new HangupResponse(channelId, "HUNG_UP");
    }

    @Override
    public TransferResponse transfer(String channelId) {
        ari.transferToOperator(channelId);
        audit.record("CALL_TRANSFER_MANUAL", "call", channelId, null);
        return new TransferResponse(channelId, "TRANSFERRING");
    }

    @Override
    public StreamingResponseBody listen(String channelId) {
        StreamingResponseBody body = ari.listen(channelId);
        audit.record("CALL_LISTEN", "call", channelId, null);
        return body;
    }
}
