package uz.murodjon.robotcallv2.aiagent.application.dto;

import java.util.List;

/**
 * The agent an import created, and what of the file it left out because it named something of
 * the exporting company (a queue, a tool, another agent, …) — the studio lists these so the
 * user can set them up again.
 */
public record AgentImportResponse(AiAgentRow agent, List<String> removed) {
}
