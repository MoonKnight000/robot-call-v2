package uz.murodjon.robotcallv2.crm.domain.entity;

import com.fasterxml.jackson.databind.JsonNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * One client as the CRM currently sees them (PROJECT.md §9 step 4).
 *
 * @param name              debtor's name, for the identity check
 * @param debtAmount        outstanding amount as of now — the figure the agent will state
 * @param currency          currency label spoken alongside the amount
 * @param dueDate           original due date
 * @param contractNumber    contract reference
 * @param preferredLanguage BCP-47 language this client prefers.
 * @param penaltyAmount     penalty accrued on the overdue amount, or {@code null} when
 *                          the CRM does not track one — the agent states this figure and
 *                          never computes one of its own
 * @param contractCancelDays days of further non-payment before the contract is cancelled
 *                          under its own terms, or {@code null} when there is no such
 *                          clause to state
 * @param leadId            the CRM's own id of the lead this snapshot was read from, or {@code null}
 *                          when the connector does not report one — what a chat that learned the
 *                          person's number writes its note onto
 */
public record CrmClientSnapshot(
        String name,
        BigDecimal debtAmount,
        String currency,
        LocalDate dueDate,
        String contractNumber,
        String preferredLanguage,
        BigDecimal penaltyAmount,
        BigDecimal contractCancelDays,
        Long leadId
) {

    public CrmClientSnapshot(String name, BigDecimal debtAmount, String currency, LocalDate dueDate,
                             String contractNumber, String preferredLanguage, BigDecimal penaltyAmount,
                             BigDecimal contractCancelDays) {
        this(name, debtAmount, currency, dueDate, contractNumber, preferredLanguage, penaltyAmount,
                contractCancelDays, null);
    }

    private static final Logger log = LoggerFactory.getLogger(CrmClientSnapshot.class);

    public static CrmClientSnapshot fromJson(JsonNode root) {
        if (root == null || root.isNull()) {
            return new CrmClientSnapshot(null, null, null, null, null, null, null, null);
        }
        JsonNode n = root.hasNonNull("data") && root.get("data").isObject() ? root.get("data") : root;
        return new CrmClientSnapshot(
                text(n, "name", "clientName", "client_name", "fullName", "full_name"),
                decimal(n, "debtAmount", "debt_amount", "debt", "balance"),
                text(n, "currency"),
                date(n, "dueDate", "due_date"),
                text(n, "contractNumber", "contract_number", "contract"),
                text(n, "preferredLanguage", "preferred_language", "language"),
                decimal(n, "penaltyAmount", "penalty_amount", "penalty", "peniya"),
                decimal(n, "contractCancelDays", "contract_cancel_days", "cancel_days"),
                n.path("id").canConvertToLong() && n.path("id").asLong() > 0 ? n.path("id").asLong() : null);
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
                return LocalDate.parse(raw.length() > 10 ? raw.substring(0, 10) : raw);
            } catch (Exception e) {
                log.warn("CRM field {} is not a date: {}", name, raw);
            }
        }
        return null;
    }
}
