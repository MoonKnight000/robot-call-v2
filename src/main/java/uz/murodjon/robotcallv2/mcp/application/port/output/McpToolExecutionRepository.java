package uz.murodjon.robotcallv2.mcp.application.port.output;

import uz.murodjon.robotcallv2.mcp.domain.entity.McpToolExecution;

/**
 * The log of what was called and how long it took.
 *
 * <p>Write-only from the application's side for now: it exists so a customer complaining
 * that "the bot went quiet" can be shown which of their tools took four seconds.
 */
public interface McpToolExecutionRepository {

    void record(McpToolExecution execution);
}
