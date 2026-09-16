package uz.murodjon.robotcallv2.notification.infrastructure.adapter;

import jakarta.mail.internet.MimeMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Component;

import uz.murodjon.robotcallv2.notification.application.port.output.NotificationSenderPort;
import uz.murodjon.robotcallv2.notification.domain.enums.NotificationChannelType;
import uz.murodjon.robotcallv2.notification.domain.enums.NotificationType;
import uz.murodjon.robotcallv2.notification.infrastructure.config.NotificationProperties;

/**
 * Delivers one notification event over SMTP (§11 settings).
 */
@Component
public class SmtpNotificationSenderAdapter implements NotificationSenderPort {

    private static final Logger log = LoggerFactory.getLogger(SmtpNotificationSenderAdapter.class);

    private final JavaMailSender mailSender;
    private final NotificationProperties notificationProperties;

    public SmtpNotificationSenderAdapter(JavaMailSender mailSender, NotificationProperties notificationProperties) {
        this.mailSender = mailSender;
        this.notificationProperties = notificationProperties;
    }

    @Override
    public NotificationChannelType resolveChannelType() {
        return NotificationChannelType.EMAIL;
    }

    @Override
    public void send(String to, NotificationType type, String title, String message, String link,
                     String signingSecret) {
        try {
            MimeMessage mail = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(mail, false, "UTF-8");
            helper.setFrom(notificationProperties.emailFrom());
            helper.setTo(to);
            helper.setSubject(title);
            helper.setText(message == null ? "" : message);
            mailSender.send(mail);
        } catch (Exception e) {
            log.warn("Notification email to {} failed: {}", to, e.getMessage());
        }
    }
}
