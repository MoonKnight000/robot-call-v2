package uz.murodjon.robotcallv2.sms.application.port.output;

import uz.murodjon.robotcallv2.sms.domain.entity.SmsMessage;

public interface SmsSenderPort {

    /**
     * Hands {@code message} to the gateway, from the company's own account when it has connected one.
     *
     * @return whether the gateway accepted it for delivery — {@code false} for a refusal,
     *         a failure or a gateway that is not configured, never an exception
     */
    boolean send(long companyId, SmsMessage message);

    /** Whether the gateway accepts this login; {@code false} also when it cannot be reached. */
    boolean verifyLogin(String email, String password);
}
