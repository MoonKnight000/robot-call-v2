package uz.murodjon.robotcallv2.tool.domain.enums;

/**
 * What calling a tool does to the system it calls — the split {@code McpToolGuard} makes
 * for MCP tools, brought to the company's own REST tools (UNIVERSAL-OPERATOR-PLAN B.3).
 *
 * <p>The model is on a live call with a stranger who can say anything, so this is about
 * what a talked-into model could do, not about what the tool is for.
 */
public enum ToolEffect {

    /** Only reads — a status lookup, a catalog search. Offered as it is. */
    READ,

    /**
     * Writes something a person can still put right — an order, a booking, a lead. Sent
     * with an {@code Idempotency-Key}, and read back for the caller's yes first when the
     * tool {@code requiresConfirmation}.
     */
    WRITE,

    /**
     * Deletes or cancels what cannot be put back. Never offered to the model: no wording
     * of "are you sure" survives a determined caller. The panel's test run still reaches it.
     */
    DESTRUCTIVE
}
