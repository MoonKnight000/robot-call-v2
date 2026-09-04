package uz.murodjon.robotcallv2.dialer.application.mapper;

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
 * {@link FactField} list actually declares or well-known memory keys are read.
 */
public final class CallContextMapper {

    private static final Logger log = LoggerFactory.getLogger(CallContextMapper.class);
    private static final ObjectMapper MAPPER = new ObjectMapper();

    private static final List<String> MEMORY_KEYS = List.of(
            "operatorNotes", "lastCallSummary", "previousCallSummary", "memoryNotes", "preferredName");

    private CallContextMapper() {
    }

    public static CallContext fromJson(String json, String goalFallback, List<FactField> factSchema) {
        Map<String, Object> facts = new HashMap<>();
        String goal = goalFallback;

        if (json != null && !json.isBlank()) {
            try {
                JsonNode n = MAPPER.readTree(json);
                if (factSchema != null) {
                    for (FactField f : factSchema) {
                        Object value = coerce(n, f.name(), f.type());
                        if (value != null) {
                            facts.put(f.name(), value);
                        }
                    }
                }
                for (String memoryKey : MEMORY_KEYS) {
                    if (n.hasNonNull(memoryKey)) {
                        facts.put(memoryKey, n.get(memoryKey).asText());
                    }
                }
                String g = text(n, "goal");
                if (g != null && !g.isBlank()) {
                    goal = g;
                }
            } catch (Exception e) {
                log.warn("Failed to parse context_data '{}': {}", json, e.getMessage());
            }
        }
        return new CallContext(facts, goal);
    }

    private static Object coerce(JsonNode n, String field, String type) {
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
     * Overlay what the CRM says over the imported facts (§9 step 4).
     */
    public static CallContext merge(CallContext imported, CrmClientSnapshot crm) {
        if (crm == null) {
            return imported;
        }
        Map<String, Object> facts = new HashMap<>(imported.facts());
        overlay(facts, "clientName", crm.name());
        overlay(facts, "debtAmount", crm.debtAmount());
        overlay(facts, "currency", crm.currency());
        overlay(facts, "dueDate", crm.dueDate());
        overlay(facts, "contractNumber", crm.contractNumber());
        overlay(facts, "penaltyAmount", crm.penaltyAmount());
        overlay(facts, "contractCancelDays", crm.contractCancelDays());
        return new CallContext(facts, imported.goal());
    }

    private static void overlay(Map<String, Object> facts, String key, Object crmValue) {
        if (crmValue != null) {
            facts.put(key, crmValue);
        }
    }

    private static String text(JsonNode n, String field) {
        return n.hasNonNull(field) ? n.get(field).asText() : null;
    }
}

