package uz.murodjon.robotcallv2.callrecord.domain.enums;

/** One step on a call's timeline (call_event.type, docs/INBOUND-PBX-PLAN.md §11.1). */
public enum CallEventType {
    /** A caller rule refused the call; payload: action, prefix. */
    REJECTED,
    /** The call started a flow; payload: flowId. */
    FLOW_ENTERED,
    /** payload: node, type. */
    NODE_ENTERED,
    /** payload: key, and extension when the caller dialled one. */
    DTMF,
    /** payload: language. */
    LANGUAGE_SET,
    /** payload: queueId, position. */
    ENQUEUED,
    /** payload: position, waitMinutes. */
    POSITION_ANNOUNCED,
    /** payload: userId (or number), channelId. */
    OPERATOR_RINGING,
    /** The operator let it ring out or rejected it; payload: userId, cause. */
    OPERATOR_MISSED,
    /** payload: userId, waitSeconds. */
    OPERATOR_ANSWERED,
    /** The operator heard the handover summary; payload: userId, seconds. */
    WHISPER,
    HOLD,
    UNHOLD,
    /** payload: from (operator userId), to ("queue 5", "user 17" or the number), mode (BLIND, CONSULT, ATTENDED). */
    TRANSFER,
    /** The caller hung up while waiting; payload: queueId, waitSeconds. */
    ABANDONED,
    /** payload: queueId, action. */
    OVERFLOW,
    /** payload: boxId, seconds. */
    VOICEMAIL,
    CALLBACK_REQUESTED,
    /** payload: score. */
    SURVEY_SCORE,
    /** A supervisor joined; payload: userId, mode. */
    MONITOR,
    /**
     * One answered agent turn, stage by stage (Audio Inspector); at = its first audio. payload,
     * in ms from the call's start: utteranceEndMs, eouWaitMs, sttFinalMs, llmRequestMs,
     * llmFirstTokenMs, ttsRequestMs, firstAudioMs; and speculated.
     */
    TURN,
    /**
     * The agent ran a function; payload: toolName, stage, arguments (what it was sent), result (what
     * came back to the model, cut to 4000 characters), success, durationMs, offsetMs (from the call's start).
     */
    TOOL_CALLED,
    /**
     * The AI agent's call entered a scenario block; payload: stage, title, type, from, via (START, BRANCH,
     * CONDITION, GLOBAL, EDGE), branch and facts (the Logic Split branch that matched and the values it
     * compared), condition (the edge's or the global block's condition), offsetMs.
     */
    STAGE_ENTERED,
    /** The AI agent's call recorded a value; payload: key, value, stage, offsetMs. */
    FACT_RECORDED,
    /**
     * The HTTP request a function sent; payload: toolName, method, url and requestBody (secrets masked),
     * headers (names only), status (or error when no answer came), responseBody, durationMs.
     */
    TOOL_HTTP,
    /** One model request's tokens; payload: stage, promptTokens, cachedTokens, completionTokens, thinkingTokens, offsetMs. */
    LLM_USAGE;

    /** Part of the call's block-by-block trace, shown under CALL_DEBUG_READ only — not on the plain timeline. */
    public boolean isCallTrace() {
        return this == TOOL_CALLED || this == STAGE_ENTERED || this == FACT_RECORDED || this == TOOL_HTTP
                || this == LLM_USAGE;
    }
}
