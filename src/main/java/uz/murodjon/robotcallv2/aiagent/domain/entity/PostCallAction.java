package uz.murodjon.robotcallv2.aiagent.domain.entity;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import uz.murodjon.robotcallv2.aiagent.domain.enums.PostCallActionType;
import uz.murodjon.robotcallv2.shared.dialog.Disposition;

import java.util.List;
import java.util.Map;

/**
 * An automated follow-up action executed after a call finishes (e.g. send SMS, call CRM webhook).
 *
 * @param id unique identifier within the agent's action list
 * @param name human-friendly title
 * @param actionType SEND_SMS, WEBHOOK, or TELEGRAM
 * @param triggerDisposition specific disposition (null for any conversion)
 * @param onlyOnSuccess true if action should only fire on successful calls (conversion or positive sentiment)
 * @param template message template supporting placeholders like {@code {{client_name}}}, {@code {{summary}}}
 * @param webhookUrl optional target endpoint if actionType == WEBHOOK
 * @param headers optional HTTP headers for webhook
 * @param enabled whether this action is active
 * @param conditions tests on the extracted values; the action runs only when all of them hold
 * @param smsRecipient SEND_SMS only: a phone number or one {@code {{field}}} placeholder; null sends to the called number
 * @param telegramBotId TELEGRAM only: the company's bot that sends; null with {@code telegramChatId} null
 *                      falls back to the company's notification channels
 * @param telegramChatId TELEGRAM only: the chat the bot writes to — a numeric id or {@code @channel}
 * @param code CODE only: a JavaScript function body reading {@code vars} (the call and its outcome so far);
 *             the fields of the object it returns join the outcome every later action reads
 */
public record PostCallAction(
        @NotBlank @Size(max = 64) String id,
        @NotBlank @Size(max = 120) String name,
        @NotNull PostCallActionType actionType,
        Disposition triggerDisposition,
        boolean onlyOnSuccess,
        String template,
        String webhookUrl,
        Map<String, String> headers,
        boolean enabled,
        List<PostCallCondition> conditions,
        String smsRecipient,
        Long telegramBotId,
        String telegramChatId,
        @Size(max = 10_000) String code
) {
    public PostCallAction {
        conditions = conditions == null ? List.of() : List.copyOf(conditions);
        smsRecipient = smsRecipient == null || smsRecipient.isBlank() ? null : smsRecipient.trim();
        telegramChatId = telegramChatId == null || telegramChatId.isBlank() ? null : telegramChatId.trim();
        code = code == null || code.isBlank() ? null : code;
    }

    public boolean isEnabled() {
        return enabled;
    }

    /** Whether every condition holds for {@code outcome}; true when there are none. */
    public boolean matchesConditions(Map<String, Object> outcome) {
        return conditions.stream().allMatch(condition -> condition.matches(outcome));
    }
}
