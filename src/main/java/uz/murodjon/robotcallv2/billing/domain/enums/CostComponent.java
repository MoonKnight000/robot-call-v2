package uz.murodjon.robotcallv2.billing.domain.enums;

/** One line of the billing screen's cost breakdown. */
public enum CostComponent {
    /** Prompt tokens billed at the full rate. */
    LLM_INPUT,
    /** Completion tokens. */
    LLM_OUTPUT,
    /** Prompt tokens served from the provider's cache. */
    LLM_CACHED,
    STT,
    TTS,
    /** A speech-to-speech engine's minutes, in place of tokens, recognition and synthesis. */
    REALTIME,
    TELEPHONY,
    PLATFORM_FEE,
    /** Chat (widget / messenger) turns. */
    CHAT,
    /** AI Copilot replies. */
    COPILOT,
    /** Questions asked about a call. */
    CALL_QUESTION,
    /** A call analysed again. */
    REANALYSIS,
    /** A token charge from a source this list does not know yet. */
    OTHER_AI
}
