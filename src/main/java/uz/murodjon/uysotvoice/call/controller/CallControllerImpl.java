package uz.murodjon.uysotvoice.call.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;

import uz.murodjon.uysotvoice.call.dto.CallOriginateResponse;
import uz.murodjon.uysotvoice.call.dto.LiveCallRow;
import uz.murodjon.uysotvoice.call.dto.PlayResponse;
import uz.murodjon.uysotvoice.call.dto.SayResponse;
import uz.murodjon.uysotvoice.call.service.CallService;
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
    public ResponseEntity<ResponseData<CallOriginateResponse>> call(String number) {
        return ResponseEntity.ok(ResponseData.ok(service.originate(number)));
    }

    @Override
    public ResponseEntity<ResponseData<PlayResponse>> play(String channelId, String file) {
        return ResponseEntity.ok(ResponseData.ok(service.play(channelId, file)));
    }

    @Override
    public ResponseEntity<ResponseData<SayResponse>> say(String channelId, String text, String language, String voice) {
        return ResponseEntity.ok(ResponseData.ok(service.say(channelId, text, language, voice)));
    }
}
