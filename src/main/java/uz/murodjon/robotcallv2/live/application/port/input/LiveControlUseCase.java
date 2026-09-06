package uz.murodjon.robotcallv2.live.application.port.input;

import uz.murodjon.robotcallv2.live.application.dto.LiveControlResponse;
import uz.murodjon.robotcallv2.live.application.dto.TakeoverRequest;
import uz.murodjon.robotcallv2.live.application.dto.WhisperRequest;

public interface LiveControlUseCase {

    LiveControlResponse injectWhisper(String channelId, WhisperRequest request);

    LiveControlResponse takeoverCall(String channelId, TakeoverRequest request);
}
