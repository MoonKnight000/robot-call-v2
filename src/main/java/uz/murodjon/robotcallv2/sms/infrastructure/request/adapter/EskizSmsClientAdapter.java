package uz.murodjon.robotcallv2.sms.infrastructure.request.adapter;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import uz.murodjon.robotcallv2.sms.application.port.output.ExternalSmsClient;

import java.net.http.HttpClient;
import java.time.Duration;

@Component
public class EskizSmsClientAdapter implements ExternalSmsClient {

    private static final Logger log = LoggerFactory.getLogger(EskizSmsClientAdapter.class);

    private final HttpClient http = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(5))
            .build();

    private final String eskizEmail;
    private final String eskizPassword;
    private final String defaultSender;

    public EskizSmsClientAdapter(
            @Value("${voice-agent.sms.eskiz.email:}") String eskizEmail,
            @Value("${voice-agent.sms.eskiz.password:}") String eskizPassword,
            @Value("${voice-agent.sms.default-sender:4546}") String defaultSender
    ) {
        this.eskizEmail = eskizEmail;
        this.eskizPassword = eskizPassword;
        this.defaultSender = defaultSender;
    }

    @Override
    public boolean send(String phone, String message) {
        log.info("Dispatching SMS via Eskiz to {}: {}", phone, message);
        // Gateway integration placeholder
        return true;
    }
}
