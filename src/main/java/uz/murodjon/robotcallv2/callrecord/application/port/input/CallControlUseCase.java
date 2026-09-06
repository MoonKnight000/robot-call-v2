package uz.murodjon.robotcallv2.callrecord.application.port.input;

import org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody;
import uz.murodjon.robotcallv2.callrecord.application.dto.*;
import uz.murodjon.robotcallv2.scenario.domain.entity.ScenarioDefinition;

import java.util.List;

public interface CallControlUseCase {
    List<LiveCallRow> liveCalls();
    CallOriginateResponse originate(long companyId, String number, Long scenarioId, Long sipTrunkId);
    CallOriginateResponse originateTestCall(long companyId, String number, ScenarioDefinition definition,
                                            Long sipTrunkId);
    WebTestCallResponse startWebTest(long companyId, WebTestCallRequest request);
    PlayResponse play(String channelId, String file);
    SayResponse say(String channelId, String text, String language, String voice);
    HangupResponse hangup(long companyId, String channelId);
    TransferResponse transfer(long companyId, String channelId);
    StreamingResponseBody listen(long companyId, String channelId);
}
