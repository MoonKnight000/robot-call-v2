package uz.murodjon.robotcallv2.mcp.domain.enums;

/** Where a connection to somebody else's MCP server has got to. */
public enum McpConnectionStatus {

    /** Saved, not yet talked to. */
    PENDING,

    /** The server answered and its tools were read. */
    CONNECTED,

    /** The server answered 401/403 — its token is missing, wrong or expired. */
    AUTH_REQUIRED,

    /** It could not be reached, or answered with something unusable. {@code lastError} says what. */
    ERROR,

    /** Switched off by the company; its tools are not offered to any agent. */
    DISABLED
}
