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
import uz.murodjon.robotcallv2.sms.application.port.output.EskizConnectionRepository;
import uz.murodjon.robotcallv2.sms.application.port.output.SmsSenderPort;
import uz.murodjon.robotcallv2.sms.domain.entity.EskizConnection;
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
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
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
    /** Eskiz's word for a message it took: queued for the operator, or already handed on. */
    private static final Set<String> ACCEPTED_STATUSES = Set.of("waiting", "success");
    /** This endpoint delivers to Uzbek numbers only; abroad is a separate Eskiz product. */
    private static final Pattern UZ_MOBILE_PHONE = Pattern.compile("^998\\d{9}$");

    private final SmsEskizProperties eskizProperties;
    private final EskizConnectionRepository connectionRepository;
    private final String sender;
    private final HttpClient httpClient;
    private final ObjectMapper objectMapper;
    private final String host;
    private final Object loginLock = new Object();
    /** The kept bearer token of each login, by its email. */
    private final Map<String, String> tokens = new ConcurrentHashMap<>();

    @Autowired
    public EskizSmsClientAdapter(SmsProperties smsProperties, ObjectMapper objectMapper,
                                 EskizConnectionRepository connectionRepository) {
        this(smsProperties, HttpClient.newBuilder().connectTimeout(CONNECT_TIMEOUT).build(), objectMapper,
                connectionRepository);
    }

    /** Lets a unit test answer for the gateway. */
    EskizSmsClientAdapter(SmsProperties smsProperties, HttpClient httpClient, ObjectMapper objectMapper,
                          EskizConnectionRepository connectionRepository) {
        this.eskizProperties = smsProperties.eskiz();
        this.connectionRepository = connectionRepository;
        this.sender = smsProperties.defaultSender();
        this.httpClient = httpClient;
        this.objectMapper = objectMapper;
        this.host = URI.create(eskizProperties.baseUrl()).getHost();
        if (smsProperties.enabled() && !eskizProperties.hasCredentials()) {
            log.warn("SMS is enabled but SMS_ESKIZ_EMAIL / SMS_ESKIZ_PASSWORD are not set — "
                    + "a company without its own Eskiz account will have its SMS reported as not sent");
        }
    }

    @Override
    public boolean send(long companyId, SmsMessage message) {
        Login login = findLogin(companyId);
        if (login == null) {
            return false;
        }
        String masked = PhoneMasker.mask(message.getPhone());
        String mobilePhone = toMobilePhone(message.getPhone());
        if (mobilePhone == null) {
            log.warn("Eskiz delivers to Uzbek numbers only — SMS to {} not sent", masked);
            return false;
        }
        try {
            String usedToken = requireToken(login);
            if (usedToken == null) {
                return false;
            }
            HttpResponse<String> response = postSms(login, usedToken, mobilePhone, message.getMessage());
            if (response.statusCode() == 401) {
                String renewedToken = renewToken(login, usedToken);
                if (renewedToken == null) {
                    return false;
                }
                response = postSms(login, renewedToken, mobilePhone, message.getMessage());
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

    @Override
    public boolean verifyLogin(String email, String password) {
        try {
            return login(new Login(email, password, sender)) != null;
        } catch (IOException e) {
            log.warn("Eskiz {} unreachable, login not checked: {}", host, e.getClass().getSimpleName());
            return false;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return false;
        }
    }

    /** The company's own account, else the platform's; {@code null} when neither is set. */
    private Login findLogin(long companyId) {
        Optional<EskizConnection> own = connectionRepository.findByCompanyId(companyId);
        if (own.isPresent()) {
            EskizConnection connection = own.get();
            return new Login(connection.email(), connection.password(),
                    connection.sender() != null ? connection.sender() : sender);
        }
        return eskizProperties.hasCredentials()
                ? new Login(eskizProperties.email(), eskizProperties.password(), sender) : null;
    }

    /** The kept token, logging in first when there is none yet; {@code null} when login fails. */
    private String requireToken(Login login) throws IOException, InterruptedException {
        String current = tokens.get(login.email());
        if (current != null) {
            return current;
        }
        synchronized (loginLock) {
            String kept = tokens.get(login.email());
            return kept != null ? kept : keep(login, login(login));
        }
    }

    /**
     * A fresh token after {@code rejectedToken} drew a 401. The first send to get here logs
     * in; one that was racing with it finds the token already replaced and reuses it.
     */
    private String renewToken(Login login, String rejectedToken) throws IOException, InterruptedException {
        synchronized (loginLock) {
            String kept = tokens.get(login.email());
            return kept == null || Objects.equals(kept, rejectedToken) ? keep(login, login(login)) : kept;
        }
    }

    private String keep(Login login, String issuedToken) {
        if (issuedToken == null) {
            tokens.remove(login.email());
        } else {
            tokens.put(login.email(), issuedToken);
        }
        return issuedToken;
    }

    private String login(Login login) throws IOException, InterruptedException {
        Map<String, String> fields = new LinkedHashMap<>();
        fields.put("email", login.email());
        fields.put("password", login.password());
        HttpResponse<String> response = httpClient.send(buildFormPost("/auth/login", fields, null),
                HttpResponse.BodyHandlers.ofString());
        String issuedToken = response.statusCode() == 200 ? readText(response.body(), "/data/token") : null;
        if (issuedToken == null) {
            log.warn("Eskiz {} login failed: HTTP {}", host, response.statusCode());
        }
        return issuedToken;
    }

    private HttpResponse<String> postSms(Login login, String bearerToken, String mobilePhone, String text)
            throws IOException, InterruptedException {
        Map<String, String> fields = new LinkedHashMap<>();
        fields.put("mobile_phone", mobilePhone);
        fields.put("message", text);
        fields.put("from", login.sender());
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

    /** One Eskiz login with the sender its messages go out under; its password is never logged. */
    private record Login(String email, String password, String sender) {
    }

    private String readText(String body, String pointer) {
        if (body == null || body.isBlank()) {
            return null;
        }
        try {
            JsonNode node = objectMapper.readTree(body).at(pointer);
            return node.isTextual() && !node.asText().isBlank() ? node.asText() : null;
        } catch (JsonProcessingException e) {
            return null;
        }
    }
}
