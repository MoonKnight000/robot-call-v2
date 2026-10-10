package uz.murodjon.robotcallv2.webhook.application.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import uz.murodjon.robotcallv2.notification.application.port.input.NotificationUseCase;
import uz.murodjon.robotcallv2.notification.domain.enums.NotificationType;
import uz.murodjon.robotcallv2.secret.application.port.input.SecretUseCase;
import uz.murodjon.robotcallv2.secret.domain.service.SecretPlaceholders;
import uz.murodjon.robotcallv2.shared.api.PageableData;
import uz.murodjon.robotcallv2.shared.exception.ConflictException;
import uz.murodjon.robotcallv2.shared.exception.ErrorCode;
import uz.murodjon.robotcallv2.shared.exception.NotFoundException;
import uz.murodjon.robotcallv2.shared.exception.ValidationException;
import uz.murodjon.robotcallv2.shared.stream.EventSink;
import uz.murodjon.robotcallv2.shared.stream.EventStreamHub;
import uz.murodjon.robotcallv2.shared.stream.EventSubscription;
import uz.murodjon.robotcallv2.shared.util.PublicUrlGuard;
import uz.murodjon.robotcallv2.shared.util.SecretCipher;
import uz.murodjon.robotcallv2.webhook.application.dto.WebhookDeliveryRow;
import uz.murodjon.robotcallv2.webhook.application.dto.WebhookLiveEvent;
import uz.murodjon.robotcallv2.webhook.application.mapper.WebhookDeliveryMapper;
import uz.murodjon.robotcallv2.webhook.application.port.input.WebhookDeliveryUseCase;
import uz.murodjon.robotcallv2.webhook.application.port.output.WebhookDeliveryRepository;
import uz.murodjon.robotcallv2.webhook.application.port.output.WebhookSenderPort;
import uz.murodjon.robotcallv2.webhook.domain.entity.WebhookDelivery;
import uz.murodjon.robotcallv2.webhook.domain.entity.WebhookDeliveryFilter;
import uz.murodjon.robotcallv2.webhook.domain.enums.WebhookDeliveryStatus;
import uz.murodjon.robotcallv2.webhook.domain.service.WebhookSigningSecret;

import java.net.URI;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Delivers post-call webhooks with retries (NEXT-PLAN S.6).
 *
 * <p>The first try is made on the caller's thread, exactly as before. A failure is written
 * to {@code webhook_delivery} and a minute sweep tries it again after 1, 5 and 30 minutes;
 * after the fourth try it is marked failed and the company is notified. Two instances
 * sweeping at once do not both send: each due row is claimed with a conditional update
 * first.
 *
 * <p>The resolved URL and headers are credentials by the time they get here, so a retry
 * row stores them only encrypted ({@link SecretCipher}) and shows just the host. The
 * signing secret is looked up again on every try, so a rotated secret signs the retry.
 */
@Service
public class WebhookDeliveryService implements WebhookDeliveryUseCase {

    private static final Logger log = LoggerFactory.getLogger(WebhookDeliveryService.class);
    private final ObjectMapper objectMapper;
    private static final TypeReference<Map<String, Object>> TARGET_TYPE = new TypeReference<>() {};

    /** Waits before the second, third and fourth tries. */
    private static final List<Duration> BACKOFF =
            List.of(Duration.ofMinutes(1), Duration.ofMinutes(5), Duration.ofMinutes(30));
    /** Tries in all, the first included. */
    private static final int MAX_ATTEMPTS = BACKOFF.size() + 1;
    /** How long a claimed row stays out of other sweeps; longer than one send can take. */
    private static final Duration CLAIM_LEASE = Duration.ofMinutes(2);
    private static final Duration KEEP_DELIVERED = Duration.ofDays(7);
    private static final int BATCH = 50;
    private static final int MAX_ERROR_CHARS = 500;

    private final WebhookSenderPort sender;
    private final WebhookDeliveryRepository repository;
    private final SecretUseCase secretUseCase;
    private final SecretCipher cipher;
    private final NotificationUseCase notificationService;
    private final Clock clock;
    private final WebhookDeliveryMapper mapper;
    private final EventStreamHub<Long> streamHub = new EventStreamHub<>("webhook-stream");

    public WebhookDeliveryService(WebhookSenderPort sender, WebhookDeliveryRepository repository,
                                  SecretUseCase secretUseCase, SecretCipher cipher,
                                  NotificationUseCase notificationService, Clock clock,
                                  ObjectMapper objectMapper, WebhookDeliveryMapper mapper) {
        this.objectMapper = objectMapper;
        this.mapper = mapper;
        this.sender = sender;
        this.repository = repository;
        this.secretUseCase = secretUseCase;
        this.cipher = cipher;
        this.notificationService = notificationService;
        this.clock = clock;
    }

    @Override
    public boolean deliver(long companyId, String source, String url, Map<String, String> headers,
                           Map<String, Object> payload) {
        Map<String, String> secrets = secretUseCase.findDecryptedSecrets(companyId);
        // Made before the first try and kept with a retry row, so every try carries the same id.
        UUID deliveryId = UUID.randomUUID();
        try {
            sender.post(url, headers, payload, WebhookSigningSecret.findIn(secrets), deliveryId.toString());
            streamHub.publish(cId -> cId == companyId, "webhook", new WebhookLiveEvent(
                    deliveryId, companyId, source, url, headers, payload, clock.instant(), true, null));
            return true;
        } catch (RuntimeException e) {
            String error = errorOf(e, secrets);
            streamHub.publish(cId -> cId == companyId, "webhook", new WebhookLiveEvent(
                    deliveryId, companyId, source, url, headers, payload, clock.instant(), false, error));
            String sealed = seal(url, headers);
            if (sealed == null) {
                log.warn("Webhook to {} failed and cannot be kept for retry — no encryption key is set: {}",
                        hostOf(url), error);
                return false;
            }
            repository.create(WebhookDelivery.failedFirstTry(companyId, deliveryId, source, hostOf(url), sealed, payload,
                    clock.instant().plus(BACKOFF.getFirst()), error));
            log.warn("Webhook to {} failed, retrying in {}: {}", hostOf(url), BACKOFF.getFirst(), error);
            return false;
        }
    }

    @Override
    public EventSubscription subscribe(long companyId, EventSink sink) {
        return streamHub.subscribe(companyId, sink);
    }

    @Scheduled(fixedDelay = 20_000)
    public void sendHeartbeats() {
        streamHub.sendHeartbeat();
    }

    @Override
    public Map<String, Object> postForReply(String url, Map<String, String> headers, Map<String, Object> payload,
                                            String signingSecret) {
        return sender.postForObject(url, headers, payload, signingSecret, UUID.randomUUID().toString());
    }

    @Scheduled(fixedDelayString = "${voice-agent.webhook.retry-check-ms:60000}", initialDelay = 60_000)
    public void retryDue() {
        Instant now = clock.instant();
        for (WebhookDelivery delivery : repository.findDue(now, BATCH)) {
            if (repository.claim(delivery.id(), now, now.plus(CLAIM_LEASE))) {
                retry(delivery, now);
            }
        }
        int removed = repository.deleteDeliveredBefore(now.minus(KEEP_DELIVERED));
        if (removed > 0) {
            log.debug("Removed {} delivered webhook row(s) older than {}", removed, KEEP_DELIVERED);
        }
    }

    private void retry(WebhookDelivery delivery, Instant now) {
        int attempt = delivery.attempts() + 1;
        // Read inside the try as well: a secret or a target that no longer decrypts — a
        // rotated key — is this try failing, not the whole sweep stopping.
        Map<String, String> secrets = Map.of();
        try {
            secrets = secretUseCase.findDecryptedSecrets(delivery.companyId());
            sendStored(delivery, secrets);
            repository.markDelivered(delivery.id(), attempt, now);
            log.info("Webhook {} to {} delivered on try {}", delivery.id(), delivery.host(), attempt);
        } catch (RuntimeException e) {
            String error = errorOf(e, secrets);
            if (attempt >= MAX_ATTEMPTS) {
                repository.markFailed(delivery.id(), attempt, error);
                log.warn("Webhook {} to {} failed {} times, giving up: {}", delivery.id(), delivery.host(), attempt, error);
                notificationService.notify(delivery.companyId(), NotificationType.WEBHOOK_DELIVERY_FAILED,
                        "Webhook yetkazilmadi",
                        delivery.host() + " manziliga " + attempt + " marta urinib ham yuborib bo'lmadi: " + error,
                        null);
                return;
            }
            repository.markRetry(delivery.id(), attempt, now.plus(BACKOFF.get(attempt - 1)), error);
            log.warn("Webhook {} to {} failed on try {}, next in {}: {}", delivery.id(), delivery.host(),
                    attempt, BACKOFF.get(attempt - 1), error);
        }
    }

    @Override
    public PageableData<WebhookDeliveryRow> filterDeliveries(long companyId, WebhookDeliveryFilter filter) {
        PageableData<WebhookDelivery> page = repository.findPageByCompanyId(companyId, filter);
        return new PageableData<>(page.totalPages(), page.currentPage(), page.totalElements(),
                page.data().stream().map(mapper::toWebhookDeliveryRow).toList());
    }

    /**
     * One try, on the caller's thread, counted with the others. The row is claimed first, so
     * a double click sends once and the sweep never picks it up halfway. A failure is not
     * scheduled again and does not notify: whoever pressed the button sees it in the answer.
     */
    @Override
    public WebhookDeliveryRow retryDelivery(long companyId, long id) {
        WebhookDelivery delivery = repository.findByCompanyIdAndId(companyId, id);
        if (delivery == null) {
            throw new NotFoundException(ErrorCode.WEBHOOK_DELIVERY_NOT_FOUND, id);
        }
        Instant now = clock.instant();
        if (delivery.status() != WebhookDeliveryStatus.FAILED
                || !repository.claimFailed(id, now.plus(CLAIM_LEASE))) {
            throw new ConflictException(ErrorCode.WEBHOOK_DELIVERY_NOT_FAILED, id, delivery.status());
        }
        int attempt = delivery.attempts() + 1;
        Map<String, String> secrets = Map.of();
        try {
            secrets = secretUseCase.findDecryptedSecrets(companyId);
            sendStored(delivery, secrets);
            repository.markDelivered(id, attempt, now);
            log.info("Webhook {} to {} delivered on manual resend", id, delivery.host());
        } catch (RuntimeException e) {
            String error = errorOf(e, secrets);
            repository.markFailed(id, attempt, error);
            log.warn("Webhook {} to {} failed on manual resend: {}", id, delivery.host(), error);
        }
        return mapper.toWebhookDeliveryRow(repository.findByCompanyIdAndId(companyId, id));
    }

    /** POSTs a kept delivery to its stored target with its own delivery id; throws when it does not get through. */
    private void sendStored(WebhookDelivery delivery, Map<String, String> secrets) {
        Map<String, Object> target = unseal(delivery);
        String url = (String) target.get("url");
        // Screened again on every try: the address was public when the first try was
        // made, and minutes later its name may point inside the network. A refusal
        // counts as a failed try, like any other.
        if (PublicUrlGuard.parsePublic(url) == null) {
            throw new ValidationException(ErrorCode.WEBHOOK_URL_INVALID, delivery.host());
        }
        sender.post(url, headersOf(target), delivery.payload(), WebhookSigningSecret.findIn(secrets),
                delivery.deliveryId().toString());
    }

    /** The URL and headers, encrypted; null when there is no key to encrypt with. */
    private String seal(String url, Map<String, String> headers) {
        if (!cipher.available()) {
            return null;
        }
        Map<String, Object> target = new LinkedHashMap<>();
        target.put("url", url);
        target.put("headers", headers == null ? Map.of() : headers);
        try {
            return cipher.encrypt(objectMapper.writeValueAsString(target));
        } catch (Exception e) {
            log.warn("Webhook target could not be sealed for retry: {}", e.getMessage());
            return null;
        }
    }

    /** The stored URL and headers; a key rotated since they were sealed fails this try like any other failure. */
    private Map<String, Object> unseal(WebhookDelivery delivery) {
        try {
            return objectMapper.readValue(cipher.decrypt(delivery.sealedTarget()), TARGET_TYPE);
        } catch (Exception e) {
            log.debug("Webhook {} target could not be decrypted: {}", delivery.id(), e.getMessage());
            throw new ConflictException(ErrorCode.WEBHOOK_TARGET_UNREADABLE, delivery.id());
        }
    }

    private static Map<String, String> headersOf(Map<String, Object> target) {
        Map<String, String> headers = new LinkedHashMap<>();
        if (target.get("headers") instanceof Map<?, ?> stored) {
            stored.forEach((name, value) -> headers.put(String.valueOf(name), String.valueOf(value)));
        }
        return headers;
    }

    /** The failure as it may be stored and shown: secrets masked, length capped. */
    private static String errorOf(RuntimeException e, Map<String, String> secrets) {
        String message = SecretPlaceholders.redact(String.valueOf(e.getMessage()), secrets.values());
        return message.length() > MAX_ERROR_CHARS ? message.substring(0, MAX_ERROR_CHARS) : message;
    }

    /** The host alone: the path and query may carry a token. */
    private static String hostOf(String url) {
        try {
            String host = URI.create(url).getHost();
            return host != null ? host : "?";
        } catch (IllegalArgumentException e) {
            return "?";
        }
    }
}
