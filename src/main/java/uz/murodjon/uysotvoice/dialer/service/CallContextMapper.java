package uz.murodjon.uysotvoice.dialer.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import uz.murodjon.uysotvoice.agent.dialog.CallContext;
import uz.murodjon.uysotvoice.crm.dto.CrmClientSnapshot;
import uz.murodjon.uysotvoice.scenario.dto.FactField;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Builds a {@link CallContext} from a target's {@code context_data} JSON (PROJECT.md
 * §6, ROADMAP A.3). Lenient — a fact missing from the JSON, or one that fails to
 * coerce to its declared type, is simply left out. Only keys the bound scenario's
 * {@link FactField} list actually declares are read; anything else in
 * {@code context_data} is ignored.
 */
public final class CallContextMapper {

    private static final Logger log = LoggerFactory.getLogger(CallContextMapper.class);
    private static final ObjectMapper MAPPER = new ObjectMapper();

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
     *
     * <p>The CRM wins field by field, because it is the authoritative source and
     * {@code context_data} is a snapshot taken when the campaign was built — a debt amount
     * imported last month may already be paid. A field the CRM does not return keeps the
     * imported value rather than becoming null: partial data is not a reason to drop facts
     * the agent needs.
     *
     * <p>Special-cased to the 5 fixed fact names {@link CrmClientSnapshot} carries — a
     * scenario whose {@code factSchema} happens to use these exact names (as
     * {@code debt-collection} does) gets the overlay; other scenarios' facts are
     * untouched. A fully generic CRM-to-facts mapping is a later ROADMAP B/A.4 concern,
     * not required here.
     *
     * @param crm the CRM snapshot, or null when the lookup was off or failed
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
        // The goal is the campaign's, not the client's — the CRM has no opinion.
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
