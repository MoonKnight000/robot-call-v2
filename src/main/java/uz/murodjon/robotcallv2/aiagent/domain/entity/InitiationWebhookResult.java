package uz.murodjon.robotcallv2.aiagent.domain.entity;

import java.util.Map;

public record InitiationWebhookResult(
        Map<String, Object> variables,
        String firstMessageOverride
) {
    public static final InitiationWebhookResult EMPTY = new InitiationWebhookResult(Map.of(), null);
}
