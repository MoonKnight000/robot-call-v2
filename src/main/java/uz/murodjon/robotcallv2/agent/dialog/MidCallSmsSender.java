package uz.murodjon.robotcallv2.agent.dialog;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import uz.murodjon.robotcallv2.callrecord.application.port.input.CallAttemptUseCase;
import uz.murodjon.robotcallv2.shared.util.PhoneMasker;
import uz.murodjon.robotcallv2.sms.application.dto.SmsSendRequest;
import uz.murodjon.robotcallv2.sms.application.port.input.SmsUseCase;

/**
 * Sends the mid-call SMS of a call to the number the call is with. Shared by the cascade
 * and the realtime engine, so both tools behind {@code sendMidCallSms} really send;
 * {@link DialogTools} guards how many and how long.
 */
@Component
public class MidCallSmsSender {

    private static final Logger log = LoggerFactory.getLogger(MidCallSmsSender.class);

    private final SmsUseCase smsUseCase;
    private final CallAttemptUseCase callAttemptUseCase;

    public MidCallSmsSender(SmsUseCase smsUseCase, CallAttemptUseCase callAttemptUseCase) {
        this.smsUseCase = smsUseCase;
        this.callAttemptUseCase = callAttemptUseCase;
    }

    /** @return whether the gateway took it; never throws, the tool reports a failure instead */
    public boolean send(long companyId, long callAttemptId, String channelId, CallContext context, String text) {
        try {
            // The number this call is with first: a "phone" fact can come from a CRM
            // record and name someone else.
            String phone = callAttemptUseCase.findPhoneByAttemptId(callAttemptId);
            if ((phone == null || phone.isBlank()) && context != null) {
                phone = context.clientPhone();
            }
            if (phone == null || phone.isBlank()) {
                log.warn("[{}] Mid-call SMS not sent: no number for this call", channelId);
                return false;
            }
            boolean sent = smsUseCase.sendSms(companyId, new SmsSendRequest(phone, text));
            // Masked number and length only: the text is a payment link or a card number.
            log.info("[{}] Mid-call SMS to {} ({} chars): sent={}", channelId, PhoneMasker.mask(phone),
                    text.length(), sent);
            return sent;
        } catch (Exception e) {
            log.warn("[{}] Failed to send mid-call SMS: {}", channelId, e.getMessage());
            return false;
        }
    }
}
