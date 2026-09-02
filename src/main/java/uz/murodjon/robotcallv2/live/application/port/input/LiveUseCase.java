package uz.murodjon.robotcallv2.live.application.port.input;

import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

public interface LiveUseCase {

    SseEmitter subscribe();
}
