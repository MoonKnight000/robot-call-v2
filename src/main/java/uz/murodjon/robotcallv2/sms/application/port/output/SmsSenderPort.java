package uz.murodjon.robotcallv2.sms.application.port.output;

import uz.murodjon.robotcallv2.sms.domain.entity.SmsMessage;

public interface SmsSenderPort {

    /**
     * Hands {@code message} to the gateway.
     *
     * @return whether the gateway accepted it for delivery — {@code false} for a refusal,
     *         a failure or a gateway that is not configured, never an exception
     */
    boolean send(SmsMessage message);
}
