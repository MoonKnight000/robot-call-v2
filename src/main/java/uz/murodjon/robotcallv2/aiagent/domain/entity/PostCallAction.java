package uz.murodjon.robotcallv2.aiagent.domain.entity;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import uz.murodjon.robotcallv2.aiagent.domain.enums.PostCallActionType;
import uz.murodjon.robotcallv2.shared.dialog.Disposition;

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
        boolean enabled
) {
    public boolean isEnabled() {
        return enabled;
    }
}
