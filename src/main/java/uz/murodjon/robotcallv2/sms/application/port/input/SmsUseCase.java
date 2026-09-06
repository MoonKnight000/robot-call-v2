package uz.murodjon.robotcallv2.sms.application.port.input;

import uz.murodjon.robotcallv2.sms.application.dto.SmsSendRequest;

public interface SmsUseCase {

    boolean sendSms(long companyId, SmsSendRequest request);

    boolean isEnabled();
}
