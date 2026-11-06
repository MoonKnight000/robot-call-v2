package uz.murodjon.robotcallv2.aiagent.domain.enums;

/** Which of an agent's two webhooks a test send imitates. */
public enum AgentWebhookKind {
    /** At call start; its JSON reply may carry {@code variables} and {@code firstMessage}. */
    INITIATION,
    /** After the call; the reply's body is not read. */
    POST_CALL
}
