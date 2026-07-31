package uz.murodjon.uysotvoice.live.controller;

import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import uz.murodjon.uysotvoice.live.service.LiveBroadcastService;

@RestController
public class LiveControllerImpl implements LiveController {

    private final LiveBroadcastService broadcast;

    public LiveControllerImpl(LiveBroadcastService broadcast) {
        this.broadcast = broadcast;
    }

    @Override
    public SseEmitter stream() {
        return broadcast.subscribe();
    }
}
