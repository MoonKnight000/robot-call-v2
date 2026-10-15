package uz.murodjon.robotcallv2.auth.application.port.input;

import uz.murodjon.robotcallv2.auth.application.dto.SignupRequest;
import uz.murodjon.robotcallv2.auth.application.dto.SignupResponse;

public interface SignupUseCase {

    /**
     * Creates a company with its first administrator and free trial money, and mails the
     * administrator an activation link. Nothing is kept if the mail cannot be sent.
     */
    SignupResponse signup(SignupRequest request);
}
