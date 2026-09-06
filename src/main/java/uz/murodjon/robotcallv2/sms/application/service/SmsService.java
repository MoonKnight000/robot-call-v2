package uz.murodjon.robotcallv2.sms.application.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import uz.murodjon.robotcallv2.audit.application.service.AuditService;
import uz.murodjon.robotcallv2.sms.application.dto.SmsSendRequest;
import uz.murodjon.robotcallv2.sms.application.port.input.SmsUseCase;
import uz.murodjon.robotcallv2.sms.application.port.output.ExternalSmsClient;
import uz.murodjon.robotcallv2.sms.domain.service.SmsValidator;

/**
 * Dispatches SMS notifications (e.g. payment links, bank card details, address info)
 * triggered either mid-call by AI tools or after call completion.
 */
@Service
public class SmsService implements SmsUseCase {

    private static final Logger log = LoggerFactory.getLogger(SmsService.class);

    private final boolean enabled;
    private final ExternalSmsClient externalSmsClient;
    private final AuditService auditService;

    public SmsService(
            @Value("${voice-agent.sms.enabled:false}") boolean enabled,
            ExternalSmsClient externalSmsClient,
            AuditService auditService
    ) {
        this.enabled = enabled;
        this.externalSmsClient = externalSmsClient;
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
    public boolean sendSms(long companyId, SmsSendRequest req) {
        SmsValidator.validate(req.phone(), req.message());

        if (!enabled) {
            log.info("[SMS DISABLED] Would send SMS to {}: {}", req.phone(), req.message());
            return true;
        }

        try {
            log.info("Sending SMS to {}: {}", req.phone(), req.message());
            boolean success = externalSmsClient.send(req.phone(), req.message());
            if (success) {
                auditService.record(companyId, "SMS_SENT", "sms", req.phone(), req.message());
            }
            return success;
        } catch (Exception e) {
            log.error("Failed to send SMS to {}: {}", req.phone(), e.getMessage());
            return false;
        }
    }
}
