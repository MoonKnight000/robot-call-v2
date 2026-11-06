package uz.murodjon.robotcallv2.aiagent.application.dto;

import jakarta.validation.constraints.NotNull;

import java.time.Instant;
import java.util.Map;

/**
 * An agent as a file (docs/api/ai-agents.md §14.6): what {@code GET /{id}/export} returns and
 * what {@code POST /import} takes back, unchanged.
 *
 * @param kind       always {@code robotcall.ai-agent}; anything else is not an agent file
 * @param format     the file layout's version; this server reads {@code 1}
 * @param exportedAt when the file was written; informational
 * @param agent      the agent's settings, a shared scenario written in as its own definition
 */
public record AgentExportFile(String kind, Integer format, Instant exportedAt, @NotNull Map<String, Object> agent) {
}
