package uz.murodjon.robotcallv2.live.presentation.controller;

import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import uz.murodjon.robotcallv2.live.application.port.input.LiveUseCase;
import uz.murodjon.robotcallv2.shared.api.SseEventStreams;

@RestController
public class LiveControllerImpl implements LiveController {

    private final LiveUseCase liveUseCase;

    public LiveControllerImpl(LiveUseCase liveUseCase) {
        this.liveUseCase = liveUseCase;
    }

    @Override
    public SseEmitter stream(long companyId) {
        return SseEventStreams.open(sink -> liveUseCase.subscribe(companyId, sink));
    }
}
