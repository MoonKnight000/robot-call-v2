package uz.murodjon.uysotvoice.dialer;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import uz.murodjon.uysotvoice.agent.crm.CrmClientSnapshot;
import uz.murodjon.uysotvoice.agent.dialog.CallContext;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * Builds a {@link CallContext} from a target's {@code context_data} JSON (PROJECT.md
 * §6). Lenient — missing fields become null. Recognized keys: {@code clientName},
 * {@code debtAmount}, {@code currency}, {@code dueDate} (yyyy-MM-dd),
 * {@code contractNumber}, {@code goal}.
 */
public final class CallContextMapper {

    private static final Logger log = LoggerFactory.getLogger(CallContextMapper.class);
    private static final ObjectMapper MAPPER = new ObjectMapper();

    private CallContextMapper() {
    }

    public static CallContext fromJson(String json, String goalFallback) {
        String clientName = null;
        BigDecimal debtAmount = null;
        String currency = null;
        LocalDate dueDate = null;
        String contractNumber = null;
        String goal = goalFallback;

        if (json != null && !json.isBlank()) {
            try {
                JsonNode n = MAPPER.readTree(json);
                clientName = text(n, "clientName");
                currency = text(n, "currency");
                contractNumber = text(n, "contractNumber");
                if (n.hasNonNull("debtAmount")) {
                    debtAmount = new BigDecimal(n.get("debtAmount").asText());
                }
                String due = text(n, "dueDate");
                if (due != null && !due.isBlank()) {
                    dueDate = LocalDate.parse(due);
                }
                String g = text(n, "goal");
                if (g != null && !g.isBlank()) {
                    goal = g;
                }
            } catch (Exception e) {
                log.warn("Failed to parse context_data '{}': {}", json, e.getMessage());
            }
        }
        return new CallContext(clientName, debtAmount, currency, dueDate, contractNumber, goal);
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
     * @param crm the CRM snapshot, or null when the lookup was off or failed
     */
    public static CallContext merge(CallContext imported, CrmClientSnapshot crm) {
        if (crm == null) {
            return imported;
        }
        return new CallContext(
                firstNonNull(crm.name(), imported.clientName()),
                firstNonNull(crm.debtAmount(), imported.debtAmount()),
                firstNonNull(crm.currency(), imported.currency()),
                firstNonNull(crm.dueDate(), imported.dueDate()),
                firstNonNull(crm.contractNumber(), imported.contractNumber()),
                // The goal is the campaign's, not the client's — the CRM has no opinion.
                imported.goal());
    }

    private static <T> T firstNonNull(T preferred, T fallback) {
        return preferred != null ? preferred : fallback;
    }

    private static String text(JsonNode n, String field) {
        return n.hasNonNull(field) ? n.get(field).asText() : null;
    }
}
