package uz.murodjon.robotcallv2.tool.domain.entity;

import uz.murodjon.robotcallv2.tool.domain.enums.ToolExecutionStatus;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.HexFormat;

/**
 * One request a company tool sent on a call, with what came back and how long it took
 * (UNIVERSAL-OPERATOR-PLAN B.6) — the REST counterpart of {@code McpToolExecution}.
 *
 * <p>What the model sent is kept as a hash, not as text: the arguments are the caller's
 * name, phone and address, and what this row is for — "create_order answers 422 on one
 * call in eight" — only needs to tell identical requests apart. A refused request keeps the
 * start of the endpoint's answer, because that is where it says what it did not like.
 *
 * @param toolId        null once the tool has been deleted
 * @param callAttemptId null for a run from the panel's Test button
 * @param httpStatus    what the endpoint answered; null when it never did
 * @param argsHash      {@link #hashArguments} of the arguments
 * @param error         the failure, or the start of a non-2xx answer; null on success
 */
public record ToolExecution(
        Long id,
        long companyId,
        Long toolId,
        Long callAttemptId,
        String toolName,
        ToolExecutionStatus status,
        Integer httpStatus,
        int latencyMs,
        String argsHash,
        String error,
        Instant createdAt
) {

    /** How much of a failure or a refused answer is kept. */
    public static final int ERROR_LIMIT = 500;

    /** The endpoint answered: 2xx is {@code OK}, anything else {@code HTTP_ERROR} with the start of its body. */
    public static ToolExecution answered(long companyId, Long toolId, Long callAttemptId, String toolName,
                                         int httpStatus, int latencyMs, String argsHash, String body) {
        boolean ok = httpStatus >= 200 && httpStatus < 300;
        return new ToolExecution(null, companyId, toolId, callAttemptId, toolName,
                ok ? ToolExecutionStatus.OK : ToolExecutionStatus.HTTP_ERROR, httpStatus, latencyMs, argsHash,
                ok ? null : truncate(body), null);
    }

    /** The request never got an answer. */
    public static ToolExecution failed(long companyId, Long toolId, Long callAttemptId, String toolName,
                                       int latencyMs, String argsHash, String error) {
        return new ToolExecution(null, companyId, toolId, callAttemptId, toolName, ToolExecutionStatus.FAILED,
                null, latencyMs, argsHash, truncate(error), null);
    }

    /** SHA-256 of the arguments as canonical JSON, in hex: equal requests hash equal. */
    public static String hashArguments(String canonicalArguments) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(canonicalArguments.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 is not available in this JVM", e);
        }
    }

    private static String truncate(String text) {
        if (text == null) {
            return null;
        }
        return text.length() <= ERROR_LIMIT ? text : text.substring(0, ERROR_LIMIT);
    }
}
