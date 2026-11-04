package uz.murodjon.robotcallv2.aiagent.domain.enums;

/** Events an AI agent may send to its configured post-call webhook during a call. */
public enum AgentWebhookEvent {
    SENTIMENT_CHANGE,
    FACT_EXTRACTED,
    TOOL_EXECUTED,
    STAGE_TRANSITION,
    TRANSFER_REQUESTED,
    VOICEMAIL_DETECTED,
    INTENT_DETECTED,
    /** A chat conversation opened (widget, Telegram or the chat API). */
    CHAT_STARTED,
    /** A chat ended, or fell silent past its TTL. */
    CHAT_ENDED,
    /** A finished chat's post-chat analysis is ready. */
    CHAT_ANALYZED
}
