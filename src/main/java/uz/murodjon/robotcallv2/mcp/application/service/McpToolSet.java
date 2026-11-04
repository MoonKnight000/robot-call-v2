package uz.murodjon.robotcallv2.mcp.application.service;

import java.util.List;

/**
 * A group of MCP tools over one feature (analytics, campaigns, scenarios, ...). Every bean is
 * picked up by {@link McpServerToolCatalog}; a tool calls only that feature's use case, behind
 * the same permission the REST API checks.
 */
public interface McpToolSet {

    List<McpToolDefinition> findTools();
}
