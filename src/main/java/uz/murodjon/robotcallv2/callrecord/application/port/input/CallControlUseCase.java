package uz.murodjon.robotcallv2.callrecord.application.port.input;

import uz.murodjon.robotcallv2.callrecord.application.dto.*;
import uz.murodjon.robotcallv2.scenario.domain.entity.ScenarioDefinition;
import uz.murodjon.robotcallv2.shared.api.AudioStreamWriter;

import java.util.List;

public interface CallControlUseCase {
    List<LiveCallRow> findLiveCalls(long companyId);
    CallOriginateResponse originate(long companyId, String number, Long scenarioId, Long sipTrunkId);
    CallOriginateResponse originateTestCall(long companyId, String number, ScenarioDefinition definition,
                                            Long sipTrunkId);
    WebTestCallResponse startWebTest(long companyId, WebTestCallRequest request);
    PlayResponse play(long companyId, String channelId, String file);
    SayResponse say(long companyId, String channelId, String text, String language, String voice);
    HangupResponse hangup(long companyId, String channelId);
    /** {@code request} null: where the agent's settings send it; otherwise the named queue, colleague or number. */
    TransferResponse transfer(long companyId, String channelId, TransferCallRequest request);

    TransferResponse hold(long companyId, String channelId);

    TransferResponse unhold(long companyId, String channelId);

    TransferResponse startConsult(long companyId, String channelId, TransferCallRequest request);

    TransferResponse completeConsult(long companyId, String channelId);

    TransferResponse cancelConsult(long companyId, String channelId);

    MonitorCallResponse monitor(long companyId, String channelId, MonitorCallRequest request);

    ClickToCallResponse clickToCall(long companyId, ClickToCallRequest request);
    /** The live call's audio as it is produced; the controller streams it to the client. */
    AudioStreamWriter listen(long companyId, String channelId);
}
