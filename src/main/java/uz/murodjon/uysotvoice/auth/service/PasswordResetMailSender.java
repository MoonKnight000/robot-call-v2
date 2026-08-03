package uz.murodjon.uysotvoice.auth.service;

import jakarta.mail.internet.MimeMessage;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Component;

/**
 * Sends the "parolni unutdingizmi" one-time token (backend-uchun-talablar.md §9) over
 * the same {@code spring.mail.*}-configured SMTP connection {@code report.service.
 * ReportEmailSender} uses for scheduled reports — separate component since this sends
 * a plain-text token, never an attachment.
 */
@Component
public class PasswordResetMailSender {

    private final JavaMailSender mailSender;
    private final String fromAddress;

    public PasswordResetMailSender(JavaMailSender mailSender,
                                   @Value("${voice-agent.security.password-reset.from:no-reply@uysot.uz}") String fromAddress) {
        this.mailSender = mailSender;
        this.fromAddress = fromAddress;
    }

    public void send(String to, String resetToken) throws Exception {
        MimeMessage message = mailSender.createMimeMessage();
        MimeMessageHelper helper = new MimeMessageHelper(message, false, "UTF-8");
        helper.setFrom(fromAddress);
        helper.setTo(to);
        helper.setSubject("Parolni tiklash");
        helper.setText("Parolingizni tiklash uchun ushbu bir martalik kodni ilovaga kiriting: "
                + resetToken + "\n\nBu so'rovni siz yubormagan bo'lsangiz, xabarni e'tiborsiz qoldiring — "
                + "parolingiz o'zgarmaydi.");
        mailSender.send(message);
    }
}
