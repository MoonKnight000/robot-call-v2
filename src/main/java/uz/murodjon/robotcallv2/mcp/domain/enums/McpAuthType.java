package uz.murodjon.robotcallv2.mcp.domain.enums;

/** How this server is told who is calling. */
public enum McpAuthType {

    /** A public server, or one that authenticates by the URL it handed out. */
    NONE,

    /**
     * {@code Authorization: Bearer <secret>}, where the secret is a named row in the
     * company's secret store rather than a value kept on the connection.
     */
    BEARER
}
