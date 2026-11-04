package uz.murodjon.robotcallv2.mcp.domain.service;

import java.util.List;

/**
 * Which server tools only read. A tool's name says what it does — list_, get_, validate_, diff_,
 * compare_, export_ and preview_ tools change nothing; every other tool writes. Announced to MCP
 * clients as {@code annotations.readOnlyHint}, and the copilot asks a person before running a
 * tool that writes.
 *
 * <p>Spring-free.
 */
public final class McpToolAccess {

    private static final List<String> READ_PREFIXES =
            List.of("list_", "get_", "validate_", "diff_", "compare_", "export_", "preview_");

    private McpToolAccess() {
    }

    public static boolean isReadOnly(String toolName) {
        return READ_PREFIXES.stream().anyMatch(toolName::startsWith);
    }
}
