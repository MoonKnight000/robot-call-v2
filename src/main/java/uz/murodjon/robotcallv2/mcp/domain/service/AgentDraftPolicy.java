package uz.murodjon.robotcallv2.mcp.domain.service;

/**
 * What a caller holding only {@code AGENT_DRAFT_EDIT} may do to an agent through the MCP server.
 *
 * <p>Calls run on the published version, so editing the draft of a published agent changes
 * nothing a caller hears. Two things are live on the draft itself, and both stay out of reach:
 * the {@code enabled} switch, and an agent never published while enabled — its calls run on the
 * draft. An agent such a caller creates is therefore created switched off.
 *
 * <p>Spring-free.
 */
public final class AgentDraftPolicy {

    private AgentDraftPolicy() {
    }

    /** True when changing this agent's draft cannot change a live call. */
    public static boolean isDraftEditable(boolean enabled, Integer publishedVersion) {
        return !enabled || publishedVersion != null;
    }
}
