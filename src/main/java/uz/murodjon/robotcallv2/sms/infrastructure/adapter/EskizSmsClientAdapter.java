package uz.murodjon.robotcallv2.sms.infrastructure.adapter;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import uz.murodjon.robotcallv2.shared.util.PhoneMasker;
import uz.murodjon.robotcallv2.shared.util.PhoneNumbers;
import uz.murodjon.robotcallv2.sms.application.port.output.SmsSenderPort;
import uz.murodjon.robotcallv2.sms.domain.entity.SmsMessage;
import uz.murodjon.robotcallv2.sms.infrastructure.config.SmsEskizProperties;
import uz.murodjon.robotcallv2.sms.infrastructure.config.SmsProperties;

import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * Sends SMS through Eskiz.uz: log in once for a bearer token, keep it, and log in again
 * when the gateway answers 401 — an Eskiz token expires after thirty days and that answer
 * is the only sign that it has.
 *
 * <p>Only the host, the HTTP status and Eskiz's own status word reach the log. The token,
 * the message body and the full number never do: the body is a payment link or a card
 * number, and the token sends SMS on the company's account.
 */
@Component
public class EskizSmsClientAdapter implements SmsSenderPort {

    private static final Logger log = LoggerFactory.getLogger(EskizSmsClientAdapter.class);

    private static final Duration CONNECT_TIMEOUT = Duration.ofSeconds(3);
    /** The mid-call SMS tool waits on this while the caller is on the line. */
    private static final Duration REQUEST_TIMEOUT = Duration.ofSeconds(5);
    private static final ObjectMapper JSON = new ObjectMapper();
    /** Eskiz's word for a message it took: queued for the operator, or already handed on. */
    private static final Set<String> ACCEPTED_STATUSES = Set.of("waiting", "success");
    /** This endpoint delivers to Uzbek numbers only; abroad is a separate Eskiz product. */
    private static final Pattern UZ_MOBILE_PHONE = Pattern.compile("^998\\d{9}$");

    private final SmsEskizProperties eskizProperties;
    private final String sender;
    private final HttpClient httpClient;
    private final String host;
    private final Object loginLock = new Object();
    private volatile String token;

    @Autowired
    public EskizSmsClientAdapter(SmsProperties smsProperties) {
        this(smsProperties, HttpClient.newBuilder().connectTimeout(CONNECT_TIMEOUT).build());
    }

    /** Lets a unit test answer for the gateway. */
    EskizSmsClientAdapter(SmsProperties smsProperties, HttpClient httpClient) {
        this.eskizProperties = smsProperties.eskiz();
        this.sender = smsProperties.defaultSender();
        this.httpClient = httpClient;
        this.host = URI.create(eskizProperties.baseUrl()).getHost();
        if (smsProperties.enabled() && !eskizProperties.hasCredentials()) {
            log.warn("SMS is enabled but SMS_ESKIZ_EMAIL / SMS_ESKIZ_PASSWORD are not set — "
                    + "every SMS will be reported as not sent");
        }
    }

    @Override
    public boolean send(SmsMessage message) {
        if (!eskizProperties.hasCredentials()) {
            return false;
        }
        String masked = PhoneMasker.mask(message.getPhone());
        String mobilePhone = toMobilePhone(message.getPhone());
        if (mobilePhone == null) {
            log.warn("Eskiz delivers to Uzbek numbers only — SMS to {} not sent", masked);
            return false;
        }
        try {
            String usedToken = requireToken();
            if (usedToken == null) {
                return false;
            }
            HttpResponse<String> response = postSms(usedToken, mobilePhone, message.getMessage());
            if (response.statusCode() == 401) {
                String renewedToken = renewToken(usedToken);
                if (renewedToken == null) {
                    return false;
                }
                response = postSms(renewedToken, mobilePhone, message.getMessage());
            }
            String gatewayStatus = readText(response.body(), "/status");
            boolean accepted = response.statusCode() == 200 && gatewayStatus != null
                    && ACCEPTED_STATUSES.contains(gatewayStatus.toLowerCase(Locale.ROOT));
            if (accepted) {
                log.info("Eskiz {} accepted SMS to {}: status={}", host, masked, gatewayStatus);
            } else {
                log.warn("Eskiz {} refused SMS to {}: HTTP {} status={}", host, masked,
                        response.statusCode(), gatewayStatus);
            }
            return accepted;
        } catch (IOException e) {
            log.warn("Eskiz {} unreachable, SMS to {} not sent: {}", host, masked, e.getClass().getSimpleName());
            return false;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return false;
        }
    }

    /** The kept token, logging in first when there is none yet; {@code null} when login fails. */
    private String requireToken() throws IOException, InterruptedException {
        String current = token;
        if (current != null) {
            return current;
        }
        synchronized (loginLock) {
            if (token == null) {
                token = login();
            }
            return token;
        }
    }

    /**
     * A fresh token after {@code rejectedToken} drew a 401. The first send to get here logs
     * in; one that was racing with it finds the token already replaced and reuses it.
     */
    private String renewToken(String rejectedToken) throws IOException, InterruptedException {
        synchronized (loginLock) {
            if (token == null || Objects.equals(token, rejectedToken)) {
                token = login();
            }
            return token;
        }
    }

    private String login() throws IOException, InterruptedException {
        Map<String, String> fields = new LinkedHashMap<>();
        fields.put("email", eskizProperties.email());
        fields.put("password", eskizProperties.password());
        HttpResponse<String> response = httpClient.send(buildFormPost("/auth/login", fields, null),
                HttpResponse.BodyHandlers.ofString());
        String issuedToken = response.statusCode() == 200 ? readText(response.body(), "/data/token") : null;
        if (issuedToken == null) {
            log.warn("Eskiz {} login failed: HTTP {}", host, response.statusCode());
        }
        return issuedToken;
    }

    private HttpResponse<String> postSms(String bearerToken, String mobilePhone, String text)
            throws IOException, InterruptedException {
        Map<String, String> fields = new LinkedHashMap<>();
        fields.put("mobile_phone", mobilePhone);
        fields.put("message", text);
        fields.put("from", sender);
        if (eskizProperties.callbackUrl() != null && !eskizProperties.callbackUrl().isBlank()) {
            fields.put("callback_url", eskizProperties.callbackUrl());
        }
        return httpClient.send(buildFormPost("/message/sms/send", fields, bearerToken),
                HttpResponse.BodyHandlers.ofString());
    }

    private HttpRequest buildFormPost(String path, Map<String, String> fields, String bearerToken) {
        String form = fields.entrySet().stream()
                .map(field -> URLEncoder.encode(field.getKey(), StandardCharsets.UTF_8) + "="
                        + URLEncoder.encode(field.getValue(), StandardCharsets.UTF_8))
                .collect(Collectors.joining("&"));
        HttpRequest.Builder builder = HttpRequest.newBuilder(URI.create(eskizProperties.baseUrl() + path))
                .timeout(REQUEST_TIMEOUT)
                .header("Content-Type", "application/x-www-form-urlencoded")
                .header("Accept", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(form));
        if (bearerToken != null) {
            builder.header("Authorization", "Bearer " + bearerToken);
        }
        return builder.build();
    }

    /** {@code +998 90 123-45-67} → {@code 998901234567}; {@code null} for a number Eskiz cannot deliver to. */
    private static String toMobilePhone(String phone) {
        String normalized = PhoneNumbers.normalize(phone);
        if (normalized == null) {
            return null;
        }
        String digits = normalized.replaceAll("\\D", "");
        return UZ_MOBILE_PHONE.matcher(digits).matches() ? digits : null;
    }

    private static String readText(String body, String pointer) {
        if (body == null || body.isBlank()) {
            return null;
        }
        try {
            JsonNode node = JSON.readTree(body).at(pointer);
            return node.isTextual() && !node.asText().isBlank() ? node.asText() : null;
        } catch (JsonProcessingException e) {
            return null;
        }
    }
}
