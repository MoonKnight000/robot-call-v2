package uz.murodjon.robotcallv2.dialer.application.port.input;

import uz.murodjon.robotcallv2.dialer.application.dto.InstantCallRequest;
import uz.murodjon.robotcallv2.dialer.application.dto.InstantCallResponse;

public interface InstantCallUseCase {

    InstantCallResponse trigger(long companyId, InstantCallRequest request);
}
