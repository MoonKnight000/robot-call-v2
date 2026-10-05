package uz.murodjon.robotcallv2.dialer.domain.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import uz.murodjon.robotcallv2.agent.dialog.CallContext;
import uz.murodjon.robotcallv2.crm.domain.entity.CrmClientSnapshot;
import uz.murodjon.robotcallv2.scenario.domain.entity.FactField;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Builds a {@link CallContext} from a target's {@code context_data} JSON (PROJECT.md
 * §6, ROADMAP A.3). Lenient — a fact missing from the JSON, or one that fails to
 * coerce to its declared type, is simply left out. Only keys the bound scenario's
 * {@link FactField} list actually declares are read — plus the
 * free-form {@code agentOverrides.variables} (NEXT-PLAN A.8).
 */
public final class CallContextFactory {

    private static final Logger log = LoggerFactory.getLogger(CallContextFactory.class);

    private CallContextFactory() {
    }

    public static CallContext readContextData(ObjectMapper objectMapper, String json, String goalFallback, List<FactField> factSchema) {
        Map<String, Object> facts = new HashMap<>();
        String goal = goalFallback;

        if (json != null && !json.isBlank()) {
            try {
                JsonNode n = objectMapper.readTree(json);
                if (factSchema != null) {
                    for (FactField f : factSchema) {
                        Object value = coerceFact(n, f.name(), f.type());
                        if (value != null) {
                            facts.put(f.name(), value);
                        }
                    }
                }
                String g = readText(n, "goal");
                if (g != null && !g.isBlank()) {
                    goal = g;
                }
                goal = applyOverrides(n.path("agentOverrides"), facts, goal);
            } catch (Exception e) {
                log.warn("Failed to parse context_data '{}': {}", json, e.getMessage());
            }
        }
        return new CallContext(facts, goal);
    }

    /**
     * A target's {@code agentOverrides} (NEXT-PLAN A.8, the analogue of Vapi's assistant
     * overrides): {@code variables} become facts the prompt's placeholders can use even when
     * the scenario never declared them — a declared fact of the same name keeps its typed
     * value — and {@code systemPrompt} is appended to the goal, the per-call instruction
     * slot. Returns the goal to use.
     */
    private static String applyOverrides(JsonNode overrides, Map<String, Object> facts, String goal) {
        if (!overrides.isObject()) {
            return goal;
        }
        JsonNode variables = overrides.path("variables");
        if (variables.isObject()) {
            variables.fields().forEachRemaining(entry -> {
                if (entry.getValue().isValueNode() && !entry.getValue().isNull()) {
                    facts.putIfAbsent(entry.getKey(), entry.getValue().asText());
                }
            });
        }
        String extra = readText(overrides, "systemPrompt");
        if (extra == null || extra.isBlank()) {
            return goal;
        }
        return goal == null || goal.isBlank() ? extra.trim() : goal + "\n" + extra.trim();
    }

    /**
     * The target's own opening line ({@code agentOverrides.firstMessage}), or null when it
     * set none. Applied over the agent's before dialling; an initiation webhook that answers
     * with one of its own still wins, being asked later.
     */
    public static String readFirstMessageOverride(ObjectMapper objectMapper, String json) {
        if (json == null || json.isBlank()) {
            return null;
        }
        try {
            String first = readText(objectMapper.readTree(json).path("agentOverrides"), "firstMessage");
            return first == null || first.isBlank() ? null : first.trim();
        } catch (Exception e) {
            return null;
        }
    }

    private static Object coerceFact(JsonNode n, String field, String type) {
        if (!n.hasNonNull(field)) {
            return null;
        }
        try {
            return switch (type) {
                case "number" -> new BigDecimal(n.get(field).asText());
                case "date" -> LocalDate.parse(n.get(field).asText());
                default -> n.get(field).asText();
            };
        } catch (Exception e) {
            log.warn("context_data field {} does not match its declared type {}: {}", field, type, e.getMessage());
            return null;
        }
    }

    /**
     * Overlay a fact webhook's answer over whatever the call already knows.
     *
     * <p>Applied last, over the CSV and over the CRM: the endpoint was asked seconds ago and
     * is the freshest thing on the call. Lenient in the same way as {@link #readContextData} —
     * only declared facts are read, and one that does not match its declared type is left out
     * rather than replacing a good value with a broken one. A response that is not an object
     * changes nothing.
     *
     * @param json the endpoint's body, or null when it was not called or did not answer
     */
    public static CallContext overlayWebhookFacts(ObjectMapper objectMapper, CallContext context, String json, List<FactField> factSchema) {
        if (json == null || json.isBlank() || factSchema == null || factSchema.isEmpty()) {
            return context;
        }
        try {
            JsonNode node = objectMapper.readTree(json);
            if (!node.isObject()) {
                log.warn("Fact webhook answered with {} rather than a JSON object", node.getNodeType());
                return context;
            }
            Map<String, Object> facts = new HashMap<>(context.facts());
            int applied = 0;
            for (FactField field : factSchema) {
                Object value = coerceFact(node, field.name(), field.type());
                if (value != null) {
                    facts.put(field.name(), value);
                    applied++;
                }
            }
            if (applied == 0) {
                return context;
            }
            log.info("Fact webhook refreshed {} fact(s) before dialling", applied);
            return new CallContext(facts, context.goal(), context.memory());
        } catch (Exception e) {
            log.warn("Fact webhook answer could not be read as JSON: {}", e.getMessage());
            return context;
        }
    }

    /**
     * Overlay what the CRM says over the imported facts (§9 step 4).
     */
    public static CallContext mergeCrmFacts(CallContext imported, CrmClientSnapshot crm) {
        if (crm == null) {
            return imported;
        }
        Map<String, Object> facts = new HashMap<>(imported.facts());
        putIfPresent(facts, "clientName", crm.name());
        putIfPresent(facts, "debtAmount", crm.debtAmount());
        putIfPresent(facts, "currency", crm.currency());
        putIfPresent(facts, "dueDate", crm.dueDate());
        putIfPresent(facts, "contractNumber", crm.contractNumber());
        putIfPresent(facts, "penaltyAmount", crm.penaltyAmount());
        putIfPresent(facts, "contractCancelDays", crm.contractCancelDays());
        return new CallContext(facts, imported.goal());
    }

    private static void putIfPresent(Map<String, Object> facts, String key, Object crmValue) {
        if (crmValue != null) {
            facts.put(key, crmValue);
        }
    }

    private static String readText(JsonNode n, String field) {
        return n.hasNonNull(field) ? n.get(field).asText() : null;
    }
}
