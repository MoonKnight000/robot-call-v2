package uz.murodjon.robotcallv2.callrecord.application.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import uz.murodjon.robotcallv2.agent.dialog.CallSummary;
import uz.murodjon.robotcallv2.aiagent.application.service.AgentWebhookExecutor;
import uz.murodjon.robotcallv2.aiagent.domain.entity.AiAgent;
import uz.murodjon.robotcallv2.aiagent.domain.entity.PostCallAction;
import uz.murodjon.robotcallv2.aiagent.domain.enums.PostCallActionType;
import uz.murodjon.robotcallv2.notification.application.service.NotificationService;
import uz.murodjon.robotcallv2.notification.domain.enums.NotificationType;
import uz.murodjon.robotcallv2.secret.application.port.input.SecretUseCase;
import uz.murodjon.robotcallv2.shared.dialog.Disposition;
import uz.murodjon.robotcallv2.shared.dialog.Sentiment;
import uz.murodjon.robotcallv2.shared.util.PublicUrlGuard;
import uz.murodjon.robotcallv2.sms.application.dto.SmsSendRequest;
import uz.murodjon.robotcallv2.sms.application.port.input.SmsUseCase;
import uz.murodjon.robotcallv2.webhook.application.port.output.WebhookSenderPort;
import uz.murodjon.robotcallv2.webhook.domain.service.WebhookSigningSecret;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Executes automated follow-up actions (SMS, Webhooks, Telegram alerts)
 * configured on the AI Agent after a call concludes.
 */
@Component
public class PostCallActionExecutor {

    private static final Logger log = LoggerFactory.getLogger(PostCallActionExecutor.class);

    private final SmsUseCase smsUseCase;
    private final SecretUseCase secretUseCase;
    private final NotificationService notificationService;
    private final AgentWebhookExecutor agentWebhookExecutor;
    private final WebhookSenderPort webhookSender;

    public PostCallActionExecutor(SmsUseCase smsUseCase,
                                  SecretUseCase secretUseCase,
                                  NotificationService notificationService,
                                  AgentWebhookExecutor agentWebhookExecutor,
                                  WebhookSenderPort webhookSender) {
        this.smsUseCase = smsUseCase;
        this.secretUseCase = secretUseCase;
        this.notificationService = notificationService;
        this.agentWebhookExecutor = agentWebhookExecutor;
        this.webhookSender = webhookSender;
    }

    public void executeActions(long companyId, long callAttemptId, String phone,
                               AiAgent agent, Disposition disposition, CallSummary summary) {
        if (agent == null) {
            return;
        }

        if (agent.postCallWebhook() != null && agent.postCallWebhook().webhookUrl() != null
                && !agent.postCallWebhook().webhookUrl().isBlank()) {
            try {
                agentWebhookExecutor.executePostCall(
                        companyId,
                        callAttemptId,
                        phone,
                        agent,
                        disposition,
                        null,
                        summary != null ? summary.summary() : null,
                        null,
                        summary != null ? summary.outcome() : Map.of(),
                        null
                );
            } catch (Exception e) {
                log.warn("[{}] Agent post-call webhook failed: {}", callAttemptId, e.getMessage());
            }
        }

        if (agent.postCallActions().isEmpty()) {
            return;
        }

        List<PostCallAction> actions = agent.postCallActions();
        for (PostCallAction action : actions) {
            if (!action.isEnabled()) {
                continue;
            }
            if (!matchesTrigger(action, disposition, summary)) {
                continue;
            }
            try {
                dispatch(companyId, callAttemptId, phone, action, disposition, summary);
            } catch (Exception e) {
                log.warn("[{}] Post-call action '{}' ({}) failed: {}",
                        callAttemptId, action.name(), action.actionType(), e.getMessage());
            }
        }
    }

    private boolean matchesTrigger(PostCallAction action, Disposition disposition, CallSummary summary) {
        if (action.onlyOnSuccess()) {
            boolean isConversion = disposition != null && disposition.isConversion();
            boolean isPositive = summary != null && (summary.sentiment() == Sentiment.POSITIVE || summary.sentiment() == Sentiment.NEUTRAL);
            if (!isConversion && !isPositive) {
                return false;
            }
        }
        if (action.triggerDisposition() != null) {
            return action.triggerDisposition() == disposition;
        }
        return true;
    }

    private void dispatch(long companyId, long callAttemptId, String phone,
                          PostCallAction action, Disposition disposition, CallSummary summary) {
        String renderedText = renderTemplate(action.template(), phone, disposition, summary);
        renderedText = secretUseCase.resolveSecrets(companyId, renderedText);

        if (action.actionType() == PostCallActionType.SEND_SMS) {
            if (phone != null && !phone.isBlank() && renderedText != null && !renderedText.isBlank()) {
                boolean sent = smsUseCase.sendSms(companyId, new SmsSendRequest(phone, renderedText));
                log.info("[{}] Post-call SMS sent to {}: status={}", callAttemptId, phone, sent);
            }
        } else if (action.actionType() == PostCallActionType.WEBHOOK) {
            if (action.webhookUrl() != null && !action.webhookUrl().isBlank()) {
                Map<String, Object> payload = new HashMap<>();
                payload.put("companyId", companyId);
                payload.put("callAttemptId", callAttemptId);
                payload.put("phone", phone);
                payload.put("disposition", disposition != null ? disposition.name() : null);
                if (summary != null) {
                    payload.put("summary", summary.summary());
                    payload.put("sentiment", summary.sentiment() != null ? summary.sentiment().name() : null);
                    payload.put("qaScore", summary.qaScore());
                    payload.put("commitmentScore", summary.commitmentScore());
                    payload.put("needsFollowUp", summary.needsFollowUp());
                    payload.put("followUpNote", summary.followUpNote());
                    payload.put("callbackAt", summary.callbackAt());
                    payload.put("outcome", summary.outcome());
                }

                String targetUrl = secretUseCase.resolveSecrets(companyId, action.webhookUrl());
                if (PublicUrlGuard.parsePublic(targetUrl) == null) {
                    log.warn("[{}] Post-call action webhook is not publicly callable — skipped", callAttemptId);
                    return;
                }
                String signingSecret = WebhookSigningSecret.findIn(secretUseCase.findDecryptedSecrets(companyId));
                webhookSender.post(targetUrl, resolveHeaders(companyId, action.headers()), payload, signingSecret);
                log.info("[{}] Post-call webhook delivered to {}", callAttemptId, action.webhookUrl());
            }
        } else if (action.actionType() == PostCallActionType.TELEGRAM) {
            notificationService.notify(companyId, NotificationType.OPERATOR_REQUEST,
                    action.name(),
                    renderedText != null ? renderedText : ("Qo'ng'iroq ID: " + callAttemptId + ", Natija: " + disposition),
                    null);
            log.info("[{}] Post-call Telegram notification sent", callAttemptId);
        }
    }

    /** The action's headers with their {{secrets.KEY}} placeholders filled in. */
    private Map<String, String> resolveHeaders(long companyId, Map<String, String> headers) {
        if (headers == null || headers.isEmpty()) {
            return Map.of();
        }
        Map<String, String> resolved = new LinkedHashMap<>();
        headers.forEach((name, value) -> resolved.put(name, secretUseCase.resolveSecrets(companyId, value)));
        return resolved;
    }

    private String renderTemplate(String template, String phone, Disposition disposition, CallSummary summary) {
        if (template == null) {
            return "";
        }
        String res = template;
        res = res.replace("{{phone}}", phone != null ? phone : "");
        res = res.replace("{{disposition}}", disposition != null ? disposition.name() : "");
        if (summary != null) {
            res = res.replace("{{summary}}", summary.summary() != null ? summary.summary() : "");
            res = res.replace("{{sentiment}}", summary.sentiment() != null ? summary.sentiment().name() : "");
            res = res.replace("{{qaScore}}", summary.qaScore() != null ? String.valueOf(summary.qaScore()) : "");
            if (summary.outcome() != null) {
                for (Map.Entry<String, Object> entry : summary.outcome().entrySet()) {
                    if (entry.getValue() != null) {
                        res = res.replace("{{" + entry.getKey() + "}}", String.valueOf(entry.getValue()));
                    }
                }
            }
        }
        return res;
    }
}

