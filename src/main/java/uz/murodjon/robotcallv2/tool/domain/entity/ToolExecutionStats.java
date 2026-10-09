package uz.murodjon.robotcallv2.tool.domain.entity;

/**
 * How one tool's requests went over a period, read off the {@link ToolExecution} log.
 *
 * @param toolId       null for requests of tools deleted since
 * @param p95LatencyMs the time 95% of requests stayed under, the timed-out ones included
 */
public record ToolExecutionStats(
        Long toolId,
        String toolName,
        long total,
        long ok,
        long httpError,
        long failed,
        int avgLatencyMs,
        int p95LatencyMs
) {
}
