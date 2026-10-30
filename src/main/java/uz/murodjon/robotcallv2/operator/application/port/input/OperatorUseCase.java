package uz.murodjon.robotcallv2.operator.application.port.input;

import uz.murodjon.robotcallv2.operator.application.dto.OperatorContextResponse;
import uz.murodjon.robotcallv2.operator.application.dto.OperatorJoinResponse;
import uz.murodjon.robotcallv2.operator.application.dto.OperatorTakeoverResponse;
import uz.murodjon.robotcallv2.operator.application.dto.OperatorWhisperRequest;
import uz.murodjon.robotcallv2.operator.application.dto.OperatorWhisperResponse;

public interface OperatorUseCase {

    OperatorContextResponse findContext(long companyId, String channelId);

    OperatorTakeoverResponse takeover(long companyId, String channelId, String operatorExtension);

    OperatorWhisperResponse whisper(long companyId, String channelId, OperatorWhisperRequest request);

    /** The signed-in operator accepts a call the LiveKit agent transferred to them: what to join its room with. */
    OperatorJoinResponse acceptTransfer(long companyId, String channelId);

    /** The signed-in operator turns a transferred call down. */
    void rejectTransfer(long companyId, String channelId);
}
