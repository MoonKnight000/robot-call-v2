package uz.murodjon.robotcallv2.callrecord.application.port.input;

import org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody;
import uz.murodjon.robotcallv2.callrecord.application.dto.*;
import uz.murodjon.robotcallv2.scenario.domain.entity.ScenarioDefinition;

import java.util.List;

public interface CallControlUseCase {
    List<LiveCallRow> liveCalls();
    CallOriginateResponse originate(String number, Long scenarioId);
    CallOriginateResponse originate(String number, Long scenarioId, Long sipTrunkId);
    CallOriginateResponse originateTestCall(String number, ScenarioDefinition definition);
    CallOriginateResponse originateTestCall(String number, ScenarioDefinition definition, Long sipTrunkId);
    PlayResponse play(String channelId, String file);
    SayResponse say(String channelId, String text, String language, String voice);
    HangupResponse hangup(String channelId);
    TransferResponse transfer(String channelId);
    StreamingResponseBody listen(String channelId);
}
