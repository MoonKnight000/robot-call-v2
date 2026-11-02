package uz.murodjon.robotcallv2.sms.application.port.input;

import uz.murodjon.robotcallv2.sms.application.dto.SmsSendRequest;

public interface SmsUseCase {

    boolean sendSms(long companyId, SmsSendRequest request);

    /** Whether SMS can go out for the company: the platform gateway is on, or the company has its own Eskiz account. */
    boolean isEnabled(long companyId);
}
