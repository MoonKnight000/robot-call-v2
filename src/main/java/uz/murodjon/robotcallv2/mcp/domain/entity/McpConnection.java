package uz.murodjon.robotcallv2.mcp.domain.entity;

import uz.murodjon.robotcallv2.mcp.domain.enums.McpAuthType;
import uz.murodjon.robotcallv2.mcp.domain.enums.McpConnectionStatus;

import java.time.Instant;

/**
 * A company's link to an MCP server it runs somewhere else.
 *
 * <p>The credential is not here: {@code secretKey} names a row in the company's secret
 * store, so a token lives in one place with the rest of them and a connection row can be
 * read without handing anyone a way in.
 *
 * @param allowWrites      whether tools the server does not mark read-only may be offered
 *                         to the model at all; off by default
 * @param usableToolCount  of {@code toolCount}, how many survived {@link
 *                         uz.murodjon.robotcallv2.mcp.domain.service.McpToolGuard}
 * @param toolsJson         the tool list as the last refresh read it, so a call builds its
 *                         tools from this row instead of waiting on somebody else's server
 * @param lastError        why the last refresh failed, or null
 */
public record McpConnection(
        Long id,
        long companyId,
        String name,
        String url,
        McpAuthType authType,
        String secretKey,
        McpConnectionStatus status,
        int toolCount,
        int usableToolCount,
        boolean allowWrites,
        String toolsJson,
        String lastError,
        Instant refreshedAt,
        Instant createdAt,
        Instant updatedAt
) {
    /** A connection as just saved: nothing has talked to the server yet. */
    public static McpConnection pending(long companyId, String name, String url, McpAuthType authType,
                                        String secretKey, boolean allowWrites) {
        return new McpConnection(null, companyId, name, url, authType, secretKey,
                McpConnectionStatus.PENDING, 0, 0, allowWrites, null, null, null, null, null);
    }

    /** The same connection after a successful refresh. */
    public McpConnection connected(int discovered, int usable, String toolsJson) {
        return new McpConnection(id, companyId, name, url, authType, secretKey,
                McpConnectionStatus.CONNECTED, discovered, usable, allowWrites, toolsJson, null,
                Instant.now(), createdAt, updatedAt);
    }

    /** The same connection after a refresh that did not work. */
    public McpConnection failed(McpConnectionStatus status, String error) {
        return new McpConnection(id, companyId, name, url, authType, secretKey, status,
                toolCount, usableToolCount, allowWrites, toolsJson, error, Instant.now(), createdAt, updatedAt);
    }

    /** Whether this connection's tools may be offered to an agent right now. */
    public boolean isUsable() {
        return status == McpConnectionStatus.CONNECTED;
    }
}
