package uz.murodjon.robotcallv2.report.application.service;

import jakarta.mail.internet.MimeMessage;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Component;

/**
 * Sends the scheduled report email (§10.10 "Jadval bo'yicha yuborish") over SMTP.
 */
@Component
public class ReportEmailSender {

    private final JavaMailSender mailSender;
    private final String fromAddress;

    public ReportEmailSender(JavaMailSender mailSender,
                             @Value("${voice-agent.report-schedule.from:no-reply@uysot.uz}") String fromAddress) {
        this.mailSender = mailSender;
        this.fromAddress = fromAddress;
    }

    public void send(String to, String subject, String body, byte[] attachment, String filename,
                     String contentType) throws Exception {
        MimeMessage message = mailSender.createMimeMessage();
        MimeMessageHelper helper = new MimeMessageHelper(message, true, "UTF-8");
        helper.setFrom(fromAddress);
        helper.setTo(to);
        helper.setSubject(subject);
        helper.setText(body);
        helper.addAttachment(filename, new ByteArrayResource(attachment), contentType);
        mailSender.send(message);
    }
}
