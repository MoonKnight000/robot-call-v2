package uz.murodjon.robotcallv2.mcp.domain.entity;

/**
 * One tool an MCP server says it has.
 *
 * <p>{@code inputSchema} is carried as the JSON the server sent rather than parsed: it
 * goes straight to the model as the tool's parameter schema, and re-deriving it here would
 * only be a chance to get it wrong.
 *
 * @param readOnly    the server's {@code readOnlyHint} — it promises the tool changes nothing
 * @param destructive the server's {@code destructiveHint} — it warns the tool can undo or delete
 * @param inputSchema JSON Schema for the tool's arguments, as the server wrote it
 */
public record McpTool(
        String name,
        String description,
        String inputSchema,
        boolean readOnly,
        boolean destructive
) {
}
