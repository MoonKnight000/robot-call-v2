package uz.murodjon.robotcallv2.live.application.port.input;

import uz.murodjon.robotcallv2.live.application.dto.LiveControlResponse;
import uz.murodjon.robotcallv2.live.application.dto.SwitchScenarioRequest;
import uz.murodjon.robotcallv2.live.application.dto.TakeoverRequest;
import uz.murodjon.robotcallv2.live.application.dto.WhisperRequest;

public interface LiveControlUseCase {

    LiveControlResponse injectWhisper(long companyId, String channelId, WhisperRequest request);

    LiveControlResponse takeoverCall(long companyId, String channelId, TakeoverRequest request);

    LiveControlResponse switchScenario(long companyId, String channelId, SwitchScenarioRequest request);
}
