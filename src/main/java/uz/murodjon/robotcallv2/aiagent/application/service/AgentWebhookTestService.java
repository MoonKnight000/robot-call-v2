package uz.murodjon.robotcallv2.aiagent.application.service;

import org.springframework.stereotype.Service;
import uz.murodjon.robotcallv2.aiagent.application.dto.AgentWebhookTestRequest;
import uz.murodjon.robotcallv2.aiagent.application.dto.AgentWebhookTestResponse;
import uz.murodjon.robotcallv2.aiagent.application.port.input.AgentWebhookTestUseCase;
import uz.murodjon.robotcallv2.aiagent.application.port.input.AiAgentUseCase;
import uz.murodjon.robotcallv2.aiagent.domain.entity.AiAgent;
import uz.murodjon.robotcallv2.aiagent.domain.enums.AgentWebhookKind;
import uz.murodjon.robotcallv2.secret.application.port.input.SecretUseCase;
import uz.murodjon.robotcallv2.secret.domain.service.SecretPlaceholders;
import uz.murodjon.robotcallv2.shared.dialog.Disposition;
import uz.murodjon.robotcallv2.shared.exception.ErrorCode;
import uz.murodjon.robotcallv2.shared.exception.ValidationException;
import uz.murodjon.robotcallv2.shared.util.PublicUrlGuard;
import uz.murodjon.robotcallv2.webhook.application.port.input.WebhookDeliveryUseCase;
import uz.murodjon.robotcallv2.webhook.domain.service.WebhookSigningSecret;

import java.time.Duration;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Sends a sample payload to an agent webhook the way a call would — same signature, same
 * secret placeholders, same public-address rule — so the studio can tell a working endpoint
 * from a broken one before a real call depends on it. Nothing is retried or stored.
 */
@Service
public class AgentWebhookTestService implements AgentWebhookTestUseCase {

    private static final int MAX_ERROR_CHARS = 500;

    private final AiAgentUseCase aiAgentUseCase;
    private final SecretUseCase secretUseCase;
    private final WebhookDeliveryUseCase webhookDelivery;

    public AgentWebhookTestService(AiAgentUseCase aiAgentUseCase, SecretUseCase secretUseCase,
                                   WebhookDeliveryUseCase webhookDelivery) {
        this.aiAgentUseCase = aiAgentUseCase;
        this.secretUseCase = secretUseCase;
        this.webhookDelivery = webhookDelivery;
    }

    @Override
    public AgentWebhookTestResponse testWebhook(long companyId, long id, AgentWebhookTestRequest request) {
        AiAgent agent = aiAgentUseCase.requireAgent(companyId, id);
        Map<String, String> secrets = secretUseCase.findDecryptedSecrets(companyId);
        String url = SecretPlaceholders.resolve(request.webhookUrl().trim(), secrets);
        if (PublicUrlGuard.parsePublic(url) == null) {
            throw new ValidationException(ErrorCode.WEBHOOK_URL_INVALID, request.webhookUrl());
        }
        Map<String, String> headers = new LinkedHashMap<>();
        if (request.headers() != null) {
            request.headers().forEach((name, value) -> headers.put(name, SecretPlaceholders.resolve(value, secrets)));
        }
        String signingSecret = WebhookSigningSecret.findIn(secrets);
        Map<String, Object> payload = samplePayload(companyId, agent, request.kind());

        long start = System.nanoTime();
        try {
            Map<String, Object> reply = null;
            if (request.kind() == AgentWebhookKind.INITIATION) {
                Duration timeout = request.timeoutSeconds() != null ? Duration.ofSeconds(request.timeoutSeconds()) : null;
                reply = webhookDelivery.postForReply(url, headers, payload, signingSecret, timeout);
            } else {
                webhookDelivery.postOnce(url, headers, payload, signingSecret);
            }
            return new AgentWebhookTestResponse(true, elapsedMs(start), reply, null);
        } catch (RuntimeException e) {
            return new AgentWebhookTestResponse(false, elapsedMs(start), null, errorOf(e, secrets));
        }
    }

    /** The fields a real call sends, with made-up values and {@code test: true} so a receiver can skip it. */
    private static Map<String, Object> samplePayload(long companyId, AiAgent agent, AgentWebhookKind kind) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("event", kind == AgentWebhookKind.INITIATION ? "CALL_INITIATED" : "CALL_COMPLETED");
        payload.put("test", true);
        payload.put("companyId", companyId);
        payload.put("callAttemptId", 0);
        payload.put("phone", "+998901234567");
        payload.put("agentId", agent.id());
        payload.put("agentName", agent.name());
        if (kind == AgentWebhookKind.INITIATION) {
            payload.put("channelId", "test");
            payload.put("variables", Map.of());
        } else {
            payload.put("disposition", Disposition.PROMISE_TO_PAY.name());
            payload.put("durationSeconds", 42);
            payload.put("summary", "Test call: the client agreed to pay on the 15th.");
            payload.put("outcome", Map.of());
            payload.put("occurredAt", Instant.now().toString());
        }
        return payload;
    }

    private static long elapsedMs(long start) {
        return (System.nanoTime() - start) / 1_000_000;
    }

    /** The transport's message — it may echo the URL or a header — with every secret value masked. */
    private static String errorOf(RuntimeException e, Map<String, String> secrets) {
        String message = SecretPlaceholders.redact(String.valueOf(e.getMessage()), secrets.values());
        return message.length() > MAX_ERROR_CHARS ? message.substring(0, MAX_ERROR_CHARS) : message;
    }
}
