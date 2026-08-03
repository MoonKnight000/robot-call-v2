package uz.murodjon.uysotvoice.callrecord.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody;

import uz.murodjon.uysotvoice.callrecord.dto.CallOriginateResponse;
import uz.murodjon.uysotvoice.callrecord.dto.HangupResponse;
import uz.murodjon.uysotvoice.callrecord.dto.LiveCallRow;
import uz.murodjon.uysotvoice.callrecord.dto.PlayResponse;
import uz.murodjon.uysotvoice.callrecord.dto.SayResponse;
import uz.murodjon.uysotvoice.callrecord.dto.TransferResponse;
import uz.murodjon.uysotvoice.callrecord.service.CallService;
import uz.murodjon.uysotvoice.scenario.dto.ScenarioDefinition;
import uz.murodjon.uysotvoice.shared.api.ResponseData;

import java.util.List;

@RestController
public class CallControllerImpl implements CallController {

    private final CallService service;

    public CallControllerImpl(CallService service) {
        this.service = service;
    }

    @Override
    public ResponseEntity<ResponseData<List<LiveCallRow>>> live() {
        return ResponseEntity.ok(ResponseData.ok(service.liveCalls()));
    }

    @Override
    public ResponseEntity<ResponseData<CallOriginateResponse>> call(String number, Long scenarioId) {
        return ResponseEntity.ok(ResponseData.ok(service.originate(number, scenarioId)));
    }

    @Override
    public ResponseEntity<ResponseData<CallOriginateResponse>> testCall(String number, ScenarioDefinition definition) {
        return ResponseEntity.ok(ResponseData.ok(service.originateTestCall(number, definition)));
    }

    @Override
    public ResponseEntity<ResponseData<PlayResponse>> play(String channelId, String file) {
        return ResponseEntity.ok(ResponseData.ok(service.play(channelId, file)));
    }

    @Override
    public ResponseEntity<ResponseData<SayResponse>> say(String channelId, String text, String language, String voice) {
        return ResponseEntity.ok(ResponseData.ok(service.say(channelId, text, language, voice)));
    }

    @Override
    public ResponseEntity<ResponseData<HangupResponse>> hangup(String channelId) {
        return ResponseEntity.ok(ResponseData.ok(service.hangup(channelId)));
    }

    @Override
    public ResponseEntity<ResponseData<TransferResponse>> transfer(String channelId) {
        return ResponseEntity.ok(ResponseData.ok(service.transfer(channelId)));
    }

    @Override
    public ResponseEntity<StreamingResponseBody> listen(String channelId) {
        return ResponseEntity.ok(service.listen(channelId));
    }
}
