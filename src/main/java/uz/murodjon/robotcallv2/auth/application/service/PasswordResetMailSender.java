package uz.murodjon.robotcallv2.auth.application.service;

import jakarta.mail.internet.MimeMessage;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Component;

/**
 * Sends the "Parolni unutdingizmi?" one-time code (backend-uchun-talablar.md §9), over the
 * same {@code spring.mail.*} SMTP connection the report schedule uses.
 *
 * <p>No enabled flag, unlike the report schedule: {@code POST /api/auth/forgot-password}
 * answers 200 either way, because saying whether the send worked would say whether the
 * address is registered. An unconfigured SMTP server therefore just means the mail fails
 * server-side and nobody is told.
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
