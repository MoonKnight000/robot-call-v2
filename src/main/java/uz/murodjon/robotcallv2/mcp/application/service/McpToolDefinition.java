package uz.murodjon.robotcallv2.mcp.application.service;

import uz.murodjon.robotcallv2.role.domain.enums.Permission;

import java.util.Set;

/**
 * One tool of the platform's MCP server, as a {@link McpToolSet} declares it.
 *
 * @param anyOf       the caller sees and may call the tool when it holds one of these
 * @param inputSchema the JSON schema of the arguments, as text
 */
public record McpToolDefinition(String name, String description, Set<Permission> anyOf, String inputSchema,
                                McpToolHandler handler) {
}
