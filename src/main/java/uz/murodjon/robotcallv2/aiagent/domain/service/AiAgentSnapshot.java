package uz.murodjon.robotcallv2.aiagent.domain.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import uz.murodjon.robotcallv2.aiagent.domain.entity.AiAgent;
import uz.murodjon.robotcallv2.scenario.domain.entity.ScenarioDefinition;
import uz.murodjon.robotcallv2.shared.util.Tokens;

/**
 * An {@link AiAgent} as the JSON snapshot a published version stores — also the text a
 * draft is compared with to tell whether anything changed since it was published.
 */
public final class AiAgentSnapshot {

    /**
     * Unknown properties are skipped so a snapshot written before a field was added still
     * reads back; map keys are sorted so two snapshots of the same agent produce the same
     * text and can be compared as strings.
     */
    private static final ObjectMapper JSON = new ObjectMapper()
            .findAndRegisterModules()
            .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)
            .configure(SerializationFeature.ORDER_MAP_ENTRIES_BY_KEYS, true);

    private AiAgentSnapshot() {
    }

    /**
     * The agent as snapshot text. Neither version number is part of what a version stores:
     * the published one is the version row's own, and the draft row's lock counter moves on
     * every save, which would make an unchanged draft look different from what was published.
     */
    public static String toJson(AiAgent agent) {
        try {
            return JSON.writeValueAsString(agent.withPublishedVersion(null).withVersion(0));
        } catch (JsonProcessingException e) {
            // Cannot happen for a record of plain values; a failure here is a programming error.
            throw new IllegalStateException("Agent " + agent.id() + " cannot be serialised", e);
        }
    }

    /** Whether publishing {@code draft} would change anything a call sees. The on/off switch is not versioned. */
    /** The agent as a JSON tree, keys sorted, with the fields {@link #toJson} drops left out. */
    public static JsonNode toTree(AiAgent agent) {
        return JSON.valueToTree(agent.withPublishedVersion(null).withVersion(0));
    }

    public static boolean isSameConfiguration(AiAgent draft, AiAgent published) {
        return toJson(draft).equals(toJson(published.withEnabled(draft.enabled())));
    }

    /**
     * A short, stable digest of everything a call with this agent would say and do: the
     * agent as {@link #toJson} writes it (enabled or not) plus the scenario it resolves to,
     * which a shared scenario can change without the agent changing. Two equal fingerprints
     * mean an evaluation of one holds for the other.
     */
    public static String fingerprint(AiAgent agent, ScenarioDefinition scenario) {
        try {
            String text = toJson(agent.withEnabled(true)) + "\n" + JSON.writeValueAsString(scenario);
            return Tokens.hash(text);
        } catch (JsonProcessingException e) {
            // The scenario is a record of plain values.
            throw new IllegalStateException("Agent " + agent.id() + " cannot be fingerprinted", e);
        }
    }

    public static AiAgent fromJson(String json) {
        try {
            return JSON.readValue(json, AiAgent.class);
        } catch (JsonProcessingException e) {
            // Only a corrupted or hand-edited row gets here — a server fault, not a bad request.
            throw new IllegalStateException("Stored agent snapshot cannot be read", e);
        }
    }
}
