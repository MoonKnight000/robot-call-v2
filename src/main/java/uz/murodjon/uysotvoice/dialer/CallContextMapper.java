package uz.murodjon.uysotvoice.dialer;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
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

    private static String text(JsonNode n, String field) {
        return n.hasNonNull(field) ? n.get(field).asText() : null;
    }
}
