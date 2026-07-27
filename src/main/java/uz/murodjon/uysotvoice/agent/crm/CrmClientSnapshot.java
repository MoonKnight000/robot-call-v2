package uz.murodjon.uysotvoice.agent.crm;

import com.fasterxml.jackson.databind.JsonNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * One client as the CRM currently sees them (PROJECT.md §9 step 4).
 *
 * <p>Every field is nullable: a CRM that omits one is saying "no value", and the imported
 * {@code context_data} then supplies it. Only what the CRM actually returns overrides the
 * snapshot the campaign was built from.
 *
 * @param name              debtor's name, for the identity check
 * @param debtAmount        outstanding amount as of now — the figure the agent will state
 * @param currency          currency label spoken alongside the amount
 * @param dueDate           original due date
 * @param contractNumber    contract reference
 * @param preferredLanguage BCP-47 language this client prefers. The most reliable language
 *                          signal there is (§3.1 priority 1) — better than the campaign
 *                          default, and far better than guessing from the audio
 */
public record CrmClientSnapshot(
        String name,
        BigDecimal debtAmount,
        String currency,
        LocalDate dueDate,
        String contractNumber,
        String preferredLanguage
) {

    private static final Logger log = LoggerFactory.getLogger(CrmClientSnapshot.class);

    /**
     * Read a CRM client payload. Lenient by design: an unexpected shape costs the fields it
     * could not read, never the call.
     *
     * <p>Both {@code camelCase} and {@code snake_case} spellings are accepted for the
     * multi-word keys, because which one a CRM uses is not something this service gets to
     * decide.
     */
    public static CrmClientSnapshot fromJson(JsonNode root) {
        if (root == null || root.isNull()) {
            return new CrmClientSnapshot(null, null, null, null, null, null);
        }
        // Some APIs wrap the entity; unwrap one level of "data" if that is what came back.
        JsonNode n = root.hasNonNull("data") && root.get("data").isObject() ? root.get("data") : root;
        return new CrmClientSnapshot(
                text(n, "name", "clientName", "client_name", "fullName", "full_name"),
                decimal(n, "debtAmount", "debt_amount", "debt", "balance"),
                text(n, "currency"),
                date(n, "dueDate", "due_date"),
                text(n, "contractNumber", "contract_number", "contract"),
                text(n, "preferredLanguage", "preferred_language", "language"));
    }

    private static String text(JsonNode n, String... names) {
        for (String name : names) {
            if (n.hasNonNull(name)) {
                String value = n.get(name).asText();
                if (!value.isBlank()) {
                    return value;
                }
            }
        }
        return null;
    }

    private static BigDecimal decimal(JsonNode n, String... names) {
        for (String name : names) {
            if (!n.hasNonNull(name)) {
                continue;
            }
            try {
                return new BigDecimal(n.get(name).asText());
            } catch (NumberFormatException e) {
                log.warn("CRM field {} is not a number: {}", name, n.get(name).asText());
            }
        }
        return null;
    }

    private static LocalDate date(JsonNode n, String... names) {
        for (String name : names) {
            if (!n.hasNonNull(name)) {
                continue;
            }
            String raw = n.get(name).asText();
            try {
                // Tolerate a full timestamp where a date was expected.
                return LocalDate.parse(raw.length() > 10 ? raw.substring(0, 10) : raw);
            } catch (Exception e) {
                log.warn("CRM field {} is not a date: {}", name, raw);
            }
        }
        return null;
    }
}
