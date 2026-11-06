package uz.murodjon.robotcallv2.billing.domain.enums;

/** What an LLM charge was for. */
public enum LlmUsagePurpose {
    /** The conversation itself. */
    CALL,
    /** The post-call summary. */
    SUMMARY,
    /** An operator call's scorecard. */
    ANALYSIS,
    /** A call analysed again. */
    REANALYSIS,
    /** A chat (widget / messenger) turn. */
    CHAT,
    /** An AI Copilot reply. */
    COPILOT,
    /** A question asked about a call. */
    CALL_QUESTION,
    /** An ACCUMULATE contact field merged after a call. */
    CONTACT_FIELD_MERGE
}
