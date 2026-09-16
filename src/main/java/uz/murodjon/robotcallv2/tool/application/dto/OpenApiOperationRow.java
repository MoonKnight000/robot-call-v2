package uz.murodjon.robotcallv2.tool.application.dto;

import java.util.List;

/**
 * One operation of a document, to choose whether to import it ({@code POST /api/tools/import/operations}).
 *
 * @param toolName          the name the tool would get; null when it cannot be one
 * @param importable        false when the operation cannot become a tool — {@code unsupportedReason} says why
 * @param secretKeys        the secrets the tool would read
 */
public record OpenApiOperationRow(
        String operationId,
        String method,
        String path,
        String summary,
        String toolName,
        boolean importable,
        String unsupportedReason,
        List<String> secretKeys
) {
}
