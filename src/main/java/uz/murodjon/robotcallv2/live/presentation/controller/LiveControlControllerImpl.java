package uz.murodjon.robotcallv2.live.presentation.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;

import uz.murodjon.robotcallv2.live.application.dto.LiveControlResponse;
import uz.murodjon.robotcallv2.live.application.dto.TakeoverRequest;
import uz.murodjon.robotcallv2.live.application.dto.WhisperRequest;
import uz.murodjon.robotcallv2.live.application.port.input.LiveControlUseCase;
import uz.murodjon.robotcallv2.shared.api.ResponseData;

@RestController
public class LiveControlControllerImpl implements LiveControlController {

    private final LiveControlUseCase liveControlUseCase;

    public LiveControlControllerImpl(LiveControlUseCase liveControlUseCase) {
        this.liveControlUseCase = liveControlUseCase;
    }

    @Override
    public ResponseEntity<ResponseData<LiveControlResponse>> whisper(String channelId, WhisperRequest request) {
        return ResponseEntity.ok(ResponseData.ok(liveControlUseCase.injectWhisper(channelId, request)));
    }

    @Override
    public ResponseEntity<ResponseData<LiveControlResponse>> takeover(String channelId, TakeoverRequest request) {
        return ResponseEntity.ok(ResponseData.ok(liveControlUseCase.takeoverCall(channelId, request)));
    }
}
