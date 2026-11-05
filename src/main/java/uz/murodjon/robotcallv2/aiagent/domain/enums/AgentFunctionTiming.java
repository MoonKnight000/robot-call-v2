package uz.murodjon.robotcallv2.aiagent.domain.enums;

/**
 * Whether the turn that calls a CUSTOM or INTEGRATION function waits for its answer — the agent's own
 * setting over the tool's "wait for result". Lookups that run before the call are not a timing: they are
 * the agent's {@code preCallFunctions}, whose arguments come from templates rather than from the model.
 */
public enum AgentFunctionTiming {

    /** The turn waits and the model replies with the answer — what the caller is asking about (a flat search). */
    IN_TURN,

    /**
     * Started and not waited for: the turn goes on at once and spends no model round on the answer — a write the
     * caller does not need to hear back (a CRM task, a note). Its mapped fields and status variable reach the call
     * when it finishes. A tool that holds its calls for the caller's yes still reads them back in the turn first.
     */
    BACKGROUND
}
