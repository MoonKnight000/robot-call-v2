package uz.murodjon.robotcallv2.operator.application.port.input;

import uz.murodjon.robotcallv2.agent.dialog.OperatorSnapshot;

public interface OperatorUseCase {

    OperatorSnapshot snapshot(String channelId);

    void takeover(String channelId, String operatorExtension);

    void whisper(String channelId, String message);
}
