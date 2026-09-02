package uz.murodjon.robotcallv2.agent.dialog;

/** What the engine should do about the current silence. */
public enum NoInputAction {
    /** Line is not idle (or not idle long enough) — nothing to do. */
    NONE,
    /** Ask whether the caller is still there. */
    PROMPT,
    /** Prompts are exhausted; end the call. */
    END
}
