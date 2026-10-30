package uz.murodjon.robotcallv2.aiagent.domain.enums;

/**
 * Optional agent settings an edit can explicitly remove ({@code clearFields} of
 * {@code PUT /api/ai-agents/{id}}). A null field in that request keeps the agent's value, so
 * these are the ones a form needs a separate way to empty:
 * - {@link #INITIATION_WEBHOOK}, {@link #POST_CALL_WEBHOOK}: the call-start and call-end webhooks.
 * - {@link #PRE_CONVERSATION_AUDIO_FILE_ID}: the recording played before the agent speaks.
 * - {@link #MID_CALL_SMS_TEMPLATE}: the SMS text sent during the call.
 * - {@link #VOICEMAIL_MESSAGE}: what the agent says to an answering machine.
 * - {@link #FAST_LLM_MODEL}: the optional second, faster model.
 * - {@link #GREETING_PAUSE}: the agent's own pause before speaking, back to the platform default.
 * - {@link #SPEECH_LANGUAGES}: additional speech languages, back to single-language mode.
 */
public enum AgentClearableField {
    INITIATION_WEBHOOK,
    POST_CALL_WEBHOOK,
    PRE_CONVERSATION_AUDIO_FILE_ID,
    MID_CALL_SMS_TEMPLATE,
    VOICEMAIL_MESSAGE,
    FAST_LLM_MODEL,
    GREETING_PAUSE,
    SPEECH_LANGUAGES
}
