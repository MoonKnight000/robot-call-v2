package uz.murodjon.robotcallv2.live.presentation.controller;

import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import uz.murodjon.robotcallv2.live.application.port.input.LiveUseCase;

@RestController
public class LiveControllerImpl implements LiveController {

    private final LiveUseCase liveUseCase;

    public LiveControllerImpl(LiveUseCase liveUseCase) {
        this.liveUseCase = liveUseCase;
    }

    @Override
    public SseEmitter stream() {
        return liveUseCase.subscribe();
    }
}
