package uz.murodjon.robotcallv2.sms.application.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import uz.murodjon.robotcallv2.audit.application.port.input.AuditUseCase;
import uz.murodjon.robotcallv2.sms.application.dto.SmsSendRequest;
import uz.murodjon.robotcallv2.sms.application.port.input.SmsUseCase;
import uz.murodjon.robotcallv2.sms.application.port.output.SmsSenderPort;
import uz.murodjon.robotcallv2.sms.domain.entity.SmsMessage;
import uz.murodjon.robotcallv2.sms.domain.enums.SmsStatus;
import uz.murodjon.robotcallv2.sms.infrastructure.config.SmsProperties;
import uz.murodjon.robotcallv2.sms.domain.service.SmsValidator;
import uz.murodjon.robotcallv2.shared.util.PhoneMasker;

/**
 * Dispatches SMS notifications (e.g. payment links, bank card details, address info)
 * triggered either mid-call by AI tools or after call completion.
 */
@Service
public class SmsService implements SmsUseCase {

    private static final Logger log = LoggerFactory.getLogger(SmsService.class);

    private final boolean enabled;
    private final SmsSenderPort smsSender;
    private final AuditUseCase auditService;

    public SmsService(
            SmsProperties smsProperties,
            SmsSenderPort smsSender,
            AuditUseCase auditService
    ) {
        this.enabled = smsProperties.enabled();
        this.smsSender = smsSender;
        this.auditService = auditService;
    }

    @Override
    public boolean isEnabled() {
        return enabled;
    }

    /**
     * Send SMS to a target phone number. Best-effort delivery.
     */
    @Override
    public boolean sendSms(long companyId, SmsSendRequest request) {
        SmsValidator.validate(request.phone(), request.message());

        // Masked number and the length only: an SMS body is a payment link, a card number or
        // an address, and neither a log line nor the audit trail is the place to keep it.
        String masked = PhoneMasker.mask(request.phone());
        if (!enabled) {
            // Not sent, so not "true": the mid-call tool tells the caller what this returns.
            log.info("[SMS DISABLED] Would send SMS to {} ({} chars)", masked, request.message().length());
            return false;
        }

        SmsMessage sms = new SmsMessage(request.phone(), request.message());
        try {
            log.info("Sending SMS to {} ({} chars)", masked, sms.getMessage().length());
            if (smsSender.send(sms)) {
                sms.markSent();
            } else {
                sms.markFailed();
            }
        } catch (Exception e) {
            sms.markFailed();
            log.error("Failed to send SMS to {}: {}", masked, e.getMessage());
        }
        if (sms.getStatus() != SmsStatus.SENT) {
            log.warn("SMS to {} was not accepted by the gateway", masked);
            return false;
        }
        auditService.record(companyId, "SMS_SENT", "sms", masked, sms.getMessage().length() + " chars");
        return true;
    }
}
