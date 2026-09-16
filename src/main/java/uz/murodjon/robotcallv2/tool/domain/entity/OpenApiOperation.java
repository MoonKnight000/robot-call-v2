package uz.murodjon.robotcallv2.tool.domain.entity;

import java.util.List;

/**
 * One operation of an OpenAPI document, as the tool it would become (UNIVERSAL-OPERATOR-PLAN B.2).
 *
 * @param operationId       the document's own id, or {@code <method>_<path>} when it gives none
 * @param method            upper case: {@code GET}, {@code POST}, …
 * @param tool              the tool, not saved yet; null when the operation cannot be one
 * @param secretKeys        the secrets the tool reads, for the company to set
 * @param unsupportedReason why there is no tool; null when there is one
 */
public record OpenApiOperation(
        String operationId,
        String method,
        String path,
        String summary,
        Tool tool,
        List<String> secretKeys,
        String unsupportedReason
) {
}
