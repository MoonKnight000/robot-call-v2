package uz.murodjon.robotcallv2.live.presentation.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;

import uz.murodjon.robotcallv2.live.application.dto.LiveControlResponse;
import uz.murodjon.robotcallv2.live.application.dto.SwitchScenarioRequest;
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
    public ResponseEntity<ResponseData<LiveControlResponse>> whisper(long companyId, String channelId,
                                                                    WhisperRequest request) {
        return ResponseEntity.ok(ResponseData.ok(liveControlUseCase.injectWhisper(companyId, channelId, request)));
    }

    @Override
    public ResponseEntity<ResponseData<LiveControlResponse>> takeover(long companyId, String channelId,
                                                                     TakeoverRequest request) {
        return ResponseEntity.ok(ResponseData.ok(liveControlUseCase.takeoverCall(companyId, channelId, request)));
    }

    @Override
    public ResponseEntity<ResponseData<LiveControlResponse>> switchScenario(long companyId, String channelId,
                                                                           SwitchScenarioRequest request) {
        return ResponseEntity.ok(ResponseData.ok(liveControlUseCase.switchScenario(companyId, channelId, request)));
    }
}
