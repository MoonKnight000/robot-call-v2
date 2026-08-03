package uz.murodjon.uysotvoice.notification.service;

import jakarta.mail.internet.MimeMessage;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Component;

/**
 * Delivers one notification event over SMTP (§11 settings) — same {@code spring.mail.*}
 * connection {@code report.service.ReportEmailSender} already uses for scheduled
 * reports. Swallow-and-log on failure: a notification channel must never break the
 * event that raised it (same posture as {@code crm.service.CrmClient}).
 */
@Component
public class NotificationEmailSender {

    private static final Logger log = LoggerFactory.getLogger(NotificationEmailSender.class);

    private final JavaMailSender mailSender;
    private final String fromAddress;

    public NotificationEmailSender(JavaMailSender mailSender,
                                   @Value("${voice-agent.report-schedule.from:no-reply@uysot.uz}") String fromAddress) {
        this.mailSender = mailSender;
        this.fromAddress = fromAddress;
    }

    public void send(String to, String subject, String body) {
        try {
            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, false, "UTF-8");
            helper.setFrom(fromAddress);
            helper.setTo(to);
            helper.setSubject(subject);
            helper.setText(body);
            mailSender.send(message);
        } catch (Exception e) {
            log.warn("Notification email to {} failed: {}", to, e.getMessage());
        }
    }
}
