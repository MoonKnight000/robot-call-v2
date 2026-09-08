package uz.murodjon.robotcallv2.mcp.domain.entity;

import java.time.Instant;

/**
 * One tool call, with what it cost.
 *
 * <p>Somebody else's server answering slowly is felt as silence by the person on the
 * phone, so the latency is the point of this row. The previews are truncated: they are
 * for reading a failure back, not for keeping a copy of everything a customer's server
 * was told.
 *
 * @param status OK, ERROR or TIMEOUT
 */
public record McpToolExecution(
        Long id,
        long companyId,
        Long connectionId,
        Long callAttemptId,
        String toolName,
        String status,
        int latencyMs,
        String argsPreview,
        String resultPreview,
        String error,
        Instant createdAt
) {
    /** How much of an argument list or an answer is kept. */
    public static final int PREVIEW_LIMIT = 500;

    public static McpToolExecution succeeded(long companyId, Long connectionId, Long callAttemptId,
                                             String toolName, int latencyMs, String args, String result) {
        return new McpToolExecution(null, companyId, connectionId, callAttemptId, toolName, "OK",
                latencyMs, preview(args), preview(result), null, null);
    }

    public static McpToolExecution failed(long companyId, Long connectionId, Long callAttemptId,
                                          String toolName, int latencyMs, String args, String error) {
        return new McpToolExecution(null, companyId, connectionId, callAttemptId, toolName, "ERROR",
                latencyMs, preview(args), null, preview(error), null);
    }

    private static String preview(String text) {
        if (text == null) {
            return null;
        }
        return text.length() <= PREVIEW_LIMIT ? text : text.substring(0, PREVIEW_LIMIT);
    }
}
