package uz.murodjon.robotcallv2.aiagent.application.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import uz.murodjon.robotcallv2.aiagent.domain.enums.AgentWebhookKind;

import java.util.Map;

/**
 * A test send of an agent webhook, with the settings as the studio shows them — saved or not.
 *
 * @param kind           which hook to imitate; decides the sample payload
 * @param webhookUrl     the address; {@code {{secrets.KEY}}} placeholders are filled in
 * @param headers        extra headers, placeholders filled in the same way; null for none
 * @param timeoutSeconds how long an initiation test waits for the reply (1–30); null for the default
 */
public record AgentWebhookTestRequest(
        @NotNull AgentWebhookKind kind,
        @NotBlank String webhookUrl,
        Map<String, String> headers,
        @Min(1) @Max(30) Integer timeoutSeconds
) {
}
