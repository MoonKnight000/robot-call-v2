package uz.murodjon.robotcallv2.mcp.application.dto;

import uz.murodjon.robotcallv2.mcp.domain.enums.McpAuthType;
import uz.murodjon.robotcallv2.mcp.domain.enums.McpConnectionStatus;

import java.time.Instant;

/**
 * One connection as the console lists it.
 *
 * <p>{@code usableToolCount} beside {@code toolCount} is the number worth reading: a
 * server offering forty tools of which two survive the guard is a server whose
 * {@code allowWrites} needs a decision.
 */
public record McpConnectionRow(
        long id,
        String name,
        String url,
        McpAuthType authType,
        String secretKey,
        McpConnectionStatus status,
        int toolCount,
        int usableToolCount,
        boolean allowWrites,
        String lastError,
        Instant refreshedAt,
        Instant createdAt
) {
}
