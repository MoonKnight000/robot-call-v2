package uz.murodjon.uysotvoice.agent.dialog;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * Facts about one debtor, injected into the system prompt (PROJECT.md §4.4). These
 * are ground truth — the LLM states them but must never alter them. Populated from
 * campaign target {@code context_data} later (Stage 10); from config for Stage 7.
 *
 * @param clientName     debtor's name
 * @param debtAmount     outstanding amount (nullable)
 * @param currency       currency label (e.g. "so'm")
 * @param dueDate        original payment due date (nullable)
 * @param contractNumber contract reference (nullable)
 * @param goal           campaign goal / extra instruction appended to the prompt (nullable)
 */
public record CallContext(
        String clientName,
        BigDecimal debtAmount,
        String currency,
        LocalDate dueDate,
        String contractNumber,
        String goal
) {
}
