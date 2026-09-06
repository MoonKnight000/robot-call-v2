package uz.murodjon.robotcallv2.callrecord.presentation.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody;
import uz.murodjon.robotcallv2.callrecord.application.dto.*;
import uz.murodjon.robotcallv2.callrecord.application.port.input.CallControlUseCase;
import uz.murodjon.robotcallv2.scenario.domain.entity.ScenarioDefinition;
import uz.murodjon.robotcallv2.shared.api.ResponseData;

import java.util.List;

@RestController
public class CallControllerImpl implements CallController {

    private final CallControlUseCase useCase;

    public CallControllerImpl(CallControlUseCase useCase) {
        this.useCase = useCase;
    }

    @Override
    public ResponseEntity<ResponseData<List<LiveCallRow>>> live() {
        return ResponseEntity.ok(ResponseData.ok(useCase.liveCalls()));
    }

    @Override
    public ResponseEntity<ResponseData<CallOriginateResponse>> call(long companyId, String number, Long scenarioId, Long sipTrunkId) {
        return ResponseEntity.ok(ResponseData.ok(useCase.originate(companyId, number, scenarioId, sipTrunkId)));
    }

    @Override
    public ResponseEntity<ResponseData<CallOriginateResponse>> testCall(long companyId, String number, Long sipTrunkId, ScenarioDefinition definition) {
        return ResponseEntity.ok(ResponseData.ok(
                useCase.originateTestCall(companyId, number, definition, sipTrunkId)));
    }

    @Override
    public ResponseEntity<ResponseData<WebTestCallResponse>> webTest(long companyId, WebTestCallRequest request) {
        return ResponseEntity.ok(ResponseData.ok(useCase.startWebTest(companyId, request)));
    }

    @Override
    public ResponseEntity<ResponseData<PlayResponse>> play(String channelId, String file) {
        return ResponseEntity.ok(ResponseData.ok(useCase.play(channelId, file)));
    }

    @Override
    public ResponseEntity<ResponseData<SayResponse>> say(String channelId, String text, String language, String voice) {
        return ResponseEntity.ok(ResponseData.ok(useCase.say(channelId, text, language, voice)));
    }

    @Override
    public ResponseEntity<ResponseData<HangupResponse>> hangup(long companyId, String channelId) {
        return ResponseEntity.ok(ResponseData.ok(useCase.hangup(companyId, channelId)));
    }

    @Override
    public ResponseEntity<ResponseData<TransferResponse>> transfer(long companyId, String channelId) {
        return ResponseEntity.ok(ResponseData.ok(useCase.transfer(companyId, channelId)));
    }

    @Override
    public ResponseEntity<StreamingResponseBody> listen(long companyId, String channelId) {
        return ResponseEntity.ok(useCase.listen(companyId, channelId));
    }
}
