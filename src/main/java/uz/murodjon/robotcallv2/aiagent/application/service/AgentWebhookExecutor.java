package uz.murodjon.robotcallv2.aiagent.application.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import uz.murodjon.robotcallv2.aiagent.domain.entity.AgentWebhookConfig;
import uz.murodjon.robotcallv2.aiagent.domain.entity.AiAgent;
import uz.murodjon.robotcallv2.aiagent.domain.entity.InitiationWebhookResult;
import uz.murodjon.robotcallv2.secret.application.port.input.SecretUseCase;
import uz.murodjon.robotcallv2.shared.dialog.Disposition;
import uz.murodjon.robotcallv2.shared.util.PublicUrlGuard;
import uz.murodjon.robotcallv2.webhook.application.port.output.WebhookSenderPort;
import uz.murodjon.robotcallv2.webhook.domain.service.WebhookSigningSecret;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Executes AI Agent Initiation and Post-Call Webhooks.
 * Automatically resolves {{secrets.KEY}} in URLs and HTTP headers.
 */
@Component
public class AgentWebhookExecutor {

    private static final Logger log = LoggerFactory.getLogger(AgentWebhookExecutor.class);

    private final SecretUseCase secretUseCase;
    private final WebhookSenderPort webhookSender;

    public AgentWebhookExecutor(SecretUseCase secretUseCase, WebhookSenderPort webhookSender) {
        this.secretUseCase = secretUseCase;
        this.webhookSender = webhookSender;
    }

    /**
     * Executes the agent's initiation webhook synchronously at call start.
     * External CRM can inject dynamic variables (e.g. clientName, debtAmount) or override firstMessage.
     */
    public InitiationWebhookResult executeInitiation(
            long companyId,
            long callAttemptId,
            String channelId,
            String phone,
            AiAgent agent,
            Map<String, Object> initialVariables
    ) {
        if (agent == null || agent.initiationWebhook() == null) {
            return InitiationWebhookResult.EMPTY;
        }

        AgentWebhookConfig config = agent.initiationWebhook();
        if (config.webhookUrl() == null || config.webhookUrl().isBlank()) {
            return InitiationWebhookResult.EMPTY;
        }

        try {
            String targetUrl = resolveSecrets(companyId, config.webhookUrl());
            if (PublicUrlGuard.parsePublic(targetUrl) == null) {
                log.warn("[{}] Initiation webhook for agent {} is not publicly callable — skipped",
                        callAttemptId, agent.id());
                return InitiationWebhookResult.EMPTY;
            }

            Map<String, Object> payload = new HashMap<>();
            payload.put("event", "CALL_INITIATED");
            payload.put("companyId", companyId);
            payload.put("callAttemptId", callAttemptId);
            payload.put("channelId", channelId);
            payload.put("phone", phone);
            payload.put("agentId", agent.id());
            payload.put("agentName", agent.name());
            if (config.dynamicVariables() != null && !config.dynamicVariables().isEmpty()) {
                payload.put("configVariables", config.dynamicVariables());
            }
            if (initialVariables != null && !initialVariables.isEmpty()) {
                payload.put("variables", initialVariables);
            }

            Map<String, Object> response = webhookSender.postForObject(
                    targetUrl, resolveHeaders(companyId, config), payload, signingSecret(companyId));

            if (response != null) {
                Map<String, Object> vars = new HashMap<>();
                if (response.get("variables") instanceof Map<?, ?> mapVars) {
                    for (Map.Entry<?, ?> e : mapVars.entrySet()) {
                        vars.put(String.valueOf(e.getKey()), e.getValue());
                    }
                }
                String firstMessage = response.get("firstMessage") != null
                        ? String.valueOf(response.get("firstMessage"))
                        : null;

                log.info("[{}] Initiation webhook returned {} variables for agent {}",
                        callAttemptId, vars.size(), agent.id());
                return new InitiationWebhookResult(vars, firstMessage);
            }
        } catch (Exception e) {
            log.warn("[{}] Initiation webhook failed for agent {}: {}",
                    callAttemptId, agent.id(), e.getMessage());
        }

        return InitiationWebhookResult.EMPTY;
    }

    /**
     * Executes the agent's post-call webhook asynchronously after call completion.
     * Sends call duration, disposition, transcript, and recording details to external CRM/API.
     */
    @Async
    public void executePostCall(
            long companyId,
            long callAttemptId,
            String phone,
            AiAgent agent,
            Disposition disposition,
            Integer durationSeconds,
            String summary,
            String recordingUrl,
            Map<String, Object> outcome,
            Object transcript
    ) {
        if (agent == null || agent.postCallWebhook() == null) {
            return;
        }

        AgentWebhookConfig config = agent.postCallWebhook();
        if (config.webhookUrl() == null || config.webhookUrl().isBlank()) {
            return;
        }

        try {
            String targetUrl = resolveSecrets(companyId, config.webhookUrl());
            if (PublicUrlGuard.parsePublic(targetUrl) == null) {
                log.warn("[{}] Post-call webhook for agent {} is not publicly callable — skipped",
                        callAttemptId, agent.id());
                return;
            }

            Map<String, Object> payload = new HashMap<>();
            payload.put("event", "CALL_COMPLETED");
            payload.put("companyId", companyId);
            payload.put("callAttemptId", callAttemptId);
            payload.put("phone", phone);
            payload.put("agentId", agent.id());
            payload.put("agentName", agent.name());
            payload.put("disposition", disposition != null ? disposition.name() : null);
            payload.put("durationSeconds", durationSeconds);
            if (summary != null) {
                payload.put("summary", summary);
            }
            if (outcome != null) {
                payload.put("outcome", outcome);
            }
            if (Boolean.TRUE.equals(config.audioUrl()) && recordingUrl != null) {
                payload.put("recordingUrl", recordingUrl);
            }
            if (Boolean.TRUE.equals(config.transcript()) && transcript != null) {
                payload.put("transcript", transcript);
            }

            webhookSender.post(targetUrl, resolveHeaders(companyId, config), payload, signingSecret(companyId));
            log.info("[{}] Post-call webhook delivered to {} for agent {}",
                    callAttemptId, config.webhookUrl(), agent.id());
        } catch (Exception e) {
            log.warn("[{}] Post-call webhook failed for agent {}: {}",
                    callAttemptId, agent.id(), e.getMessage());
        }
    }

    private String resolveSecrets(long companyId, String text) {
        return text == null || text.isBlank() ? text : secretUseCase.resolveSecrets(companyId, text);
    }

    private String signingSecret(long companyId) {
        return WebhookSigningSecret.findIn(secretUseCase.findDecryptedSecrets(companyId));
    }

    /** The configured headers with their {{secrets.KEY}} placeholders filled in. */
    private Map<String, String> resolveHeaders(long companyId, AgentWebhookConfig config) {
        if (config.headers() == null || config.headers().isEmpty()) {
            return Map.of();
        }
        Map<String, String> resolved = new LinkedHashMap<>();
        config.headers().forEach((name, value) -> resolved.put(name, resolveSecrets(companyId, value)));
        return resolved;
    }
}
