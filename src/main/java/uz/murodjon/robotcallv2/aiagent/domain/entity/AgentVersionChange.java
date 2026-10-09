package uz.murodjon.robotcallv2.aiagent.domain.entity;

import com.fasterxml.jackson.databind.JsonNode;

/**
 * One setting that differs between two states of an agent.
 *
 * @param path   dotted path into the agent, e.g. {@code script.rolePrompt}; a list is compared whole
 * @param before the value in the older state, null when the setting was absent
 * @param after  the value in the newer state, null when the setting was removed
 */
public record AgentVersionChange(String path, JsonNode before, JsonNode after) {
}
