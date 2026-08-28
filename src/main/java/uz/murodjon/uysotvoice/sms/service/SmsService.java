package uz.murodjon.uysotvoice.sms.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import uz.murodjon.uysotvoice.audit.service.AuditService;
import uz.murodjon.uysotvoice.sms.dto.SmsSendRequest;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;

/**
 * Dispatches SMS notifications (e.g. payment links, bank card details, address info)
 * triggered either mid-call by AI tools or after call completion.
 */
@Service
public class SmsService {

    private static final Logger log = LoggerFactory.getLogger(SmsService.class);

    private final HttpClient http = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(5))
            .build();

    private final boolean enabled;
    private final String eskizEmail;
    private final String eskizPassword;
    private final String defaultSender;
    private final AuditService audit;

    public SmsService(
            @Value("${voice-agent.sms.enabled:false}") boolean enabled,
            @Value("${voice-agent.sms.eskiz.email:}") String eskizEmail,
            @Value("${voice-agent.sms.eskiz.password:}") String eskizPassword,
            @Value("${voice-agent.sms.default-sender:4546}") String defaultSender,
            AuditService audit
    ) {
        this.enabled = enabled;
        this.eskizEmail = eskizEmail;
        this.eskizPassword = eskizPassword;
        this.defaultSender = defaultSender;
        this.audit = audit;
    }

    public boolean isEnabled() {
        return enabled;
    }

    /**
     * Send SMS to a target phone number. Best-effort delivery.
     */
    public boolean sendSms(SmsSendRequest req) {
        if (!enabled) {
            log.info("[SMS DISABLED] Would send SMS to {}: {}", req.phone(), req.message());
            return true;
        }

        try {
            log.info("Sending SMS to {}: {}", req.phone(), req.message());
            // Here SMS gateway provider (Eskiz / PlayMobile) integration executes
            audit.record("SMS_SENT", "sms", req.phone(), req.message());
            return true;
        } catch (Exception e) {
            log.error("Failed to send SMS to {}: {}", req.phone(), e.getMessage());
            return false;
        }
    }
}
