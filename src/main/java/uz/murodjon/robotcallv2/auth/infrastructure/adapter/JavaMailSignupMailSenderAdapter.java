package uz.murodjon.robotcallv2.auth.infrastructure.adapter;

import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;
import org.springframework.mail.MailException;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Component;
import org.springframework.web.util.UriComponentsBuilder;
import uz.murodjon.robotcallv2.auth.application.port.output.SignupMailSenderPort;
import uz.murodjon.robotcallv2.auth.infrastructure.config.SignupProperties;
import uz.murodjon.robotcallv2.shared.exception.ErrorCode;
import uz.murodjon.robotcallv2.shared.exception.ExternalServiceException;

/** Sends the signup activation link over the {@code spring.mail.*} SMTP connection. */
@Component
public class JavaMailSignupMailSenderAdapter implements SignupMailSenderPort {

    private final JavaMailSender mailSender;
    private final SignupProperties signupProperties;

    public JavaMailSignupMailSenderAdapter(JavaMailSender mailSender, SignupProperties signupProperties) {
        this.mailSender = mailSender;
        this.signupProperties = signupProperties;
    }

    @Override
    public void sendActivation(String email, String companyName, String activationToken) {
        String link = UriComponentsBuilder.fromUriString(signupProperties.activationUrl())
                .queryParam("token", activationToken)
                .toUriString();
        try {
            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, false, "UTF-8");
            helper.setFrom(signupProperties.from());
            helper.setTo(email);
            helper.setSubject("Hisobingizni faollashtiring");
            helper.setText("\"" + companyName + "\" kompaniyasi ro'yxatdan o'tkazildi.\n\n"
                    + "Parol o'rnatish va hisobni faollashtirish uchun havolani oching:\n" + link + "\n\n"
                    + "Sinov uchun balansingizga bepul mablag' qo'shildi.\n"
                    + "Ro'yxatdan siz o'tmagan bo'lsangiz, xabarni e'tiborsiz qoldiring.");
            mailSender.send(message);
        } catch (MessagingException | MailException e) {
            throw new ExternalServiceException(ErrorCode.SIGNUP_MAIL_FAILED, "smtp", e, email);
        }
    }
}
