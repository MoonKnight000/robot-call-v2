package uz.murodjon.robotcallv2.auth.application.port.output;

public interface SignupMailSenderPort {

    /** Mails the activation link; throws when it could not be sent. */
    void sendActivation(String email, String companyName, String activationToken);
}
